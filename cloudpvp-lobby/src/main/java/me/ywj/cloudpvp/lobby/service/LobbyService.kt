package me.ywj.cloudpvp.lobby.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.ywj.cloudpvp.core.model.lobby.LobbyMessage
import me.ywj.cloudpvp.core.model.lobby.LobbyMessageDataTexting
import me.ywj.cloudpvp.core.model.lobby.LobbyMessageType
import me.ywj.cloudpvp.core.model.lobby.LobbyStatus
import me.ywj.cloudpvp.core.type.LobbyId
import me.ywj.cloudpvp.core.type.SteamID64
import me.ywj.cloudpvp.core.utils.LobbyUtils
import me.ywj.cloudpvp.lobby.entity.Lobby
import me.ywj.cloudpvp.lobby.entity.LobbyPlayer
import me.ywj.cloudpvp.lobby.entity.PlayerLobby
import me.ywj.cloudpvp.lobby.exceptions.LobbyBusyException
import me.ywj.cloudpvp.lobby.exceptions.LobbyNotExist
import me.ywj.cloudpvp.lobby.exceptions.PlayerAlreadyInLobbyException
import me.ywj.cloudpvp.lobby.repository.LobbyRepository
import me.ywj.cloudpvp.lobby.repository.PlayerLobbyRepository
import me.ywj.cloudpvp.lobby.utils.RedisLockUtils.withRedisLock
import org.redisson.api.RedissonClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.listener.PatternTopic
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import org.springframework.stereotype.Service

/**
 * LobbyService
 *
 * @author sheip9
 * @since 2024/10/20 16:35
 */
@Service
class LobbyService @Autowired constructor(
    val lobbyRepository: LobbyRepository,
    val playerLobbyRepository: PlayerLobbyRepository,
    val redisTemplate: RedisTemplate<String, Any>,
    val redissonClient: RedissonClient,
    val container: RedisMessageListenerContainer,
) {
    companion object {
        private const val LOCK_NAME_PREFIX = "LobbyLock:"
        private const val PLAYER_LOCK_NAME_PREFIX = "LobbyPlayerLock:"
        private const val LOCK_ATTEMPTS = 5
        private const val CREATE_LOBBY_ATTEMPTS = 8
    }

    /**
     * 创建大厅，将创建者加入大厅并返回大厅 ID。
     *
     * @return 新创建大厅的 ID
     * @throws LobbyBusyException 当多次生成 ID 后仍无法获得锁或完成创建时抛出
     * @throws PlayerAlreadyInLobbyException 当玩家已属于其他大厅时抛出
     */
    suspend fun createLobby(playerId: SteamID64): LobbyId {
        repeat(CREATE_LOBBY_ATTEMPTS) {
            val lobbyId = LobbyUtils.generateLobbyId()
            val createdLobbyId = withPlayerAndLobbyLock(playerId, lobbyId) {
                val playerLobbyOption = playerLobbyRepository.findById(playerId)
                if (playerLobbyOption.isPresent) {
                    throw PlayerAlreadyInLobbyException(playerId, playerLobbyOption.get().lobbyId)
                }

                if (lobbyRepository.existsById(lobbyId)) {
                    null
                } else {
                    // 创建实体必须走 Repository，确保写入 @RedisHash("Lobby") 管理的 keyspace。
                    lobbyRepository.save(Lobby(lobbyId).apply {
                        host = playerId
                        players!!.add(playerId)
                    })
                    playerLobbyRepository.save(PlayerLobby(playerId, lobbyId))
                    lobbyId
                }
            }
            if (createdLobbyId != null) {
                return createdLobbyId
            }
        }
        throw LobbyBusyException("Unable to create lobby after $CREATE_LOBBY_ATTEMPTS attempts")
    }

    /**
     * 查询玩家当前所在的大厅。
     *
     * @param playerId 玩家 ID
     * @return 玩家当前所在大厅；未加入大厅时返回 null
     */
    suspend fun getCurrentLobby(playerId: SteamID64): Lobby? {
        val playerLobbyOption = withContext(Dispatchers.IO) {
            playerLobbyRepository.findById(playerId)
        }
        if (!playerLobbyOption.isPresent) {
            return null
        }

        return withContext(Dispatchers.IO) {
            lobbyRepository.findById(playerLobbyOption.get().lobbyId).orElse(null)
        }
    }

    /**
     * 通过 HTTP 将玩家加入目标大厅，并返回最新房间信息。
     *
     * @param playerId 待加入大厅的玩家 ID
     * @param targetLobbyId 目标大厅 ID
     * @return 房间当前完整信息
     * @throws LobbyNotExist 当目标大厅不存在时抛出
     * @throws LobbyBusyException 当目标大厅状态正被其他操作长期占用时抛出
     * @throws PlayerAlreadyInLobbyException 当玩家已属于其他大厅时抛出
     */
    suspend fun joinLobby(playerId: SteamID64, targetLobbyId: LobbyId): Lobby {
        // 玩家索引和大厅成员列表必须在同一个组合锁内校验和写入，避免并发加入两个大厅。
        return withPlayerAndLobbyLock(playerId, targetLobbyId) {
            val playerLobbyOption = playerLobbyRepository.findById(playerId)
            if (playerLobbyOption.isPresent && playerLobbyOption.get().lobbyId != targetLobbyId) {
                throw PlayerAlreadyInLobbyException(playerId, playerLobbyOption.get().lobbyId)
            }

            // 目标大厅不存在时不能只写 PlayerLobby，否则会留下指向无效大厅的索引。
            val lobbyOption = lobbyRepository.findById(targetLobbyId)
            if (!lobbyOption.isPresent) {
                throw LobbyNotExist()
            }

            // 重复加入同一个大厅保持幂等，不重复广播 JOIN。
            val lobby = lobbyOption.get()
            // 匹配中或已匹配或游戏中的大厅拒绝新玩家加入。
            if (lobby.status != LobbyStatus.WAITING) {
                throw LobbyBusyException("Lobby ${lobby.id} is in status ${lobby.status}, cannot join")
            }
            val players = lobby.players!!
            val alreadyInLobby = players.contains(playerId)
            if (alreadyInLobby) {
                playerLobbyRepository.save(PlayerLobby(playerId, targetLobbyId))
                return@withPlayerAndLobbyLock lobby
            }
            players.add(playerId)
            lobbyRepository.save(lobby)
            playerLobbyRepository.save(PlayerLobby(playerId, targetLobbyId))
            lobby.sendMsg(LobbyMessage(LobbyMessageType.JOIN).apply {
                data = playerId
            })
            return@withPlayerAndLobbyLock lobby
        }
    }

    /**
     * 通过 HTTP 将玩家从当前大厅移除，并在大厅为空时删除大厅。
     *
     * @param playerId 待离开大厅的玩家 ID
     * @throws LobbyBusyException 当目标大厅状态正被其他操作长期占用时抛出
     */
    suspend fun leaveLobby(playerId: SteamID64) {
        val playerLobbyOption = withContext(Dispatchers.IO) {
            playerLobbyRepository.findById(playerId)
        }
        if (!playerLobbyOption.isPresent) {
            return
        }
        // 退出接口不再接收 lobbyId，先读取当前索引以确定需要组合加锁的大厅。
        val targetLobbyId = playerLobbyOption.get().lobbyId
        // 只处理进入方法时读到的大厅索引；锁释放后不能重试读取新索引，否则可能撤销并发完成的新加入。
        withPlayerAndLobbyLock(playerId, targetLobbyId) {
            val playerLobbyOption = playerLobbyRepository.findById(playerId)
            if (playerLobbyOption.isEmpty || targetLobbyId != playerLobbyOption.get().lobbyId) {
                // 可能因为并发下的锁竞争的情况下，另一个请求先到达并完成了退出流程了，那么可以直接返回并结束了。
                return@withPlayerAndLobbyLock
            }
            // 在前面的流程结束后，走到这里了，意味着现在已经对正确的ID拿到了锁了，那么这里就直接使用前面拿到的ID了，不需要再做额外的查询和检查。
            // 目前只有加入或者退出Lobby会更新索引，并且在更新的时候会同步更新Lobby和索引，能走到这里的流程肯定是索引一致的，不需要进行二次查询了
            val lobbyOption = lobbyRepository.findById(targetLobbyId)
            if (!lobbyOption.isPresent) {
                // 退出接口保持幂等：索引指向的大厅已不存在时，只清理当前玩家索引。
                // 这种情况只有纯粹的想不到的意外才会发生。可能需要考虑加入日志。
                playerLobbyRepository.deleteById(playerId)
                return@withPlayerAndLobbyLock
            }

            // 真正开始执行对象的更新
            val lobby = lobbyOption.get()
            val removed = lobby.players!!.removeAll { it == playerId }

            if (!removed) {
                // 退出接口保持幂等：玩家已不在成员列表时，只清理当前玩家索引。
                playerLobbyRepository.deleteById(playerId)
                return@withPlayerAndLobbyLock
            }

            // 如果是最后一名玩家离开时，先广播销毁事件，让所有仍订阅该频道的 WebSocket 连接自清理。
            if (lobby.players!!.isEmpty()) {
                lobby.sendMsg(LobbyMessage(LobbyMessageType.LOBBY_DESTROYED))
                lobbyRepository.deleteById(targetLobbyId)
                playerLobbyRepository.deleteById(playerId)
                return@withPlayerAndLobbyLock
            }

            val nextHost = if (lobby.host == playerId) lobby.players!!.first() else null
            nextHost?.let { lobby.host = it }

            lobbyRepository.save(lobby)
            playerLobbyRepository.deleteById(playerId)
            // 房主迁移必须等 Lobby 和玩家索引都写入成功后再发布，避免客户端先切换到未持久化的新房主。
            nextHost?.let { lobby.publishHostUpdate(it) }
            // 订阅者可能会在看到 LEAVE 之前先看到 UPDATE_HOST，但两条消息都必须反映已持久化状态。
            lobby.sendMsg(LobbyMessage(LobbyMessageType.LEAVE).apply {
                data = playerId
            })
        }
    }

    /**
     * 为当前 WebSocket 连接注册目标大厅的消息监听器，并发送一次大厅快照。
     *
     * @param player 当前 socket 绑定的玩家连接状态
     * @param targetLobbyId 目标大厅 ID
     * @return true 表示玩家已在大厅内、完成监听注册并已发送当前大厅快照
     * @throws LobbyNotExist 当目标大厅不存在时抛出
     * @throws LobbyBusyException 当目标大厅状态正被其他操作长期占用时抛出
     */
    suspend fun subscribeLobby(player: LobbyPlayer, targetLobbyId: LobbyId): Boolean {
        val subscribed = withLobbyLock(targetLobbyId) {
            val lobbyOption = lobbyRepository.findById(targetLobbyId)
            if (!lobbyOption.isPresent) {
                throw LobbyNotExist()
            }

            val lobby = lobbyOption.get()
            if (!lobby.players!!.contains(player.steamID64)) {
                return@withLobbyLock false
            }
            val topic = PatternTopic(lobby.id.toString())
            // Redis 监听注册后可能立刻收到关闭类消息；先记录大厅 ID，确保回调能按正确频道取消订阅。
            player.lobbyId = targetLobbyId
            container.addMessageListener(player.msgListener, topic)
            // 快照发送保留在 lobby 锁内，避免锁释放后 JOIN/LEAVE 等增量消息先于快照到达客户端。
            // 发送异常由 WebSocket 处理器统一捕获并清理已记录的订阅状态。
            player.sendMessage(LobbyMessage(LobbyMessageType.LOBBY_SNAPSHOT).apply {
                data = lobby
            })
            true
        }

        return subscribed
    }

    /**
     * 移除当前 WebSocket 连接注册的大厅消息监听器。
     *
     * @param player 当前 socket 绑定的玩家连接状态
     */
    fun unsubscribeLobby(player: LobbyPlayer) {
        val targetLobbyId = player.lobbyId ?: return
        container.removeMessageListener(player.msgListener, PatternTopic(targetLobbyId.toString()))
        player.lobbyId = null
    }

    /**
     * 开始匹配。只有房主可触发，将大厅状态设为 MATCHING 并通知所有玩家。
     * 匹配队列的加入操作留待后续通过 MQ 实现。
     *
     * @param lobbyId 目标大厅 ID
     * @param playerId 发起请求的玩家 ID
     * @throws LobbyNotExist 当目标大厅不存在时抛出
     * @throws LobbyBusyException 当玩家不是房主或大厅状态不正确时抛出
     */
    suspend fun startMatching(lobbyId: LobbyId, playerId: SteamID64) {
        withLobbyLock(lobbyId) {
            val lobbyOption = lobbyRepository.findById(lobbyId)
            if (!lobbyOption.isPresent) {
                throw LobbyNotExist()
            }

            val lobby = lobbyOption.get()
            if (lobby.host != playerId) {
                throw LobbyBusyException("Player $playerId is not the host of lobby $lobbyId")
            }
            if (lobby.status != LobbyStatus.WAITING) {
                throw LobbyBusyException("Lobby $lobbyId is in status ${lobby.status}, cannot start matching")
            }

            lobby.status = LobbyStatus.MATCHING
            lobbyRepository.save(lobby)
            lobby.sendMsg(LobbyMessage(LobbyMessageType.MATCH_START))
            // TODO: 将玩家加入匹配队列，通过 MQ 发送匹配请求
        }
    }

    /**
     * 停止匹配。只有房主可触发，将大厅状态恢复为 WAITING 并通知所有玩家。
     * 匹配队列的移除操作留待后续通过 MQ 实现。
     *
     * @param lobbyId 目标大厅 ID
     * @param playerId 发起请求的玩家 ID
     * @throws LobbyNotExist 当目标大厅不存在时抛出
     * @throws LobbyBusyException 当玩家不是房主或大厅状态不正确时抛出
     */
    suspend fun stopMatching(lobbyId: LobbyId, playerId: SteamID64) {
        withLobbyLock(lobbyId) {
            val lobbyOption = lobbyRepository.findById(lobbyId)
            if (!lobbyOption.isPresent) {
                throw LobbyNotExist()
            }

            val lobby = lobbyOption.get()
            if (lobby.host != playerId) {
                throw LobbyBusyException("Player $playerId is not the host of lobby $lobbyId")
            }
            if (lobby.status != LobbyStatus.MATCHING) {
                throw LobbyBusyException("Lobby $lobbyId is in status ${lobby.status}, cannot stop matching")
            }

            lobby.status = LobbyStatus.WAITING
            lobbyRepository.save(lobby)
            lobby.sendMsg(LobbyMessage(LobbyMessageType.MATCH_STOP))
            // TODO: 从匹配队列移除玩家，通过 MQ 发送取消匹配请求
        }
    }

    /**
     * 确认比赛。将确认信息通过 MQ 发送给匹配模块，由匹配模块统计所有玩家确认后通知本服务更新状态。
     *
     * @param lobbyId 目标大厅 ID
     * @param playerId 发起确认的玩家 ID
     * @throws LobbyNotExist 当目标大厅不存在时抛出
     * @throws LobbyBusyException 当大厅状态不正确或玩家不在大厅中时抛出
     */
    suspend fun confirmMatch(lobbyId: LobbyId, playerId: SteamID64) {
        withLobbyLock(lobbyId) {
            val lobbyOption = lobbyRepository.findById(lobbyId)
            if (!lobbyOption.isPresent) {
                throw LobbyNotExist()
            }

            val lobby = lobbyOption.get()
            if (lobby.status != LobbyStatus.MATCHED) {
                throw LobbyBusyException("Lobby $lobbyId is in status ${lobby.status}, cannot confirm match")
            }
            if (!lobby.players!!.contains(playerId)) {
                throw LobbyBusyException("Player $playerId is not in lobby $lobbyId")
            }

            lobby.sendMsg(LobbyMessage(LobbyMessageType.MATCH_CONFIRM).apply {
                data = playerId
            })
            // TODO: 通过 MQ 发送玩家确认消息给匹配模块，由匹配模块统计并下发确认结果
        }
    }

    /**
     * 通过 HTTP 向玩家所在大厅广播文本消息。
     *
     * 实现约定：文本消息是高频路径，不与 `leaveLobby` 串行加锁；退出和大厅销毁由锁内广播关闭连接收敛。
     *
     * @param playerId 发送消息的玩家 ID
     * @param content 文本消息内容
     * @throws LobbyNotExist 当目标大厅不存在时抛出
     */
    suspend fun sendTextMessage(playerId: SteamID64, content: String) {
        val playerLobbyOption = withContext(Dispatchers.IO) {
            playerLobbyRepository.findById(playerId)
        }
        if (!playerLobbyOption.isPresent) {
            throw LobbyNotExist()
        }

        val targetLobbyId = playerLobbyOption.get().lobbyId
        val lobbyOption = withContext(Dispatchers.IO) {
            lobbyRepository.findById(targetLobbyId)
        }
        if (!lobbyOption.isPresent) {
            throw LobbyNotExist()
        }

        val lobby = lobbyOption.get()
        if (!lobby.players!!.contains(playerId)) {
            throw LobbyNotExist()
        }
        // 这里刻意保持无锁读路径：WebSocket 连接建立在 lobby 锁内订阅监听，退出/销毁广播会负责关闭旧连接。
        // 与 leaveLobby 并发时允许一次基于当前读到快照的发送，避免所有文本消息都争用 Redis 锁。
        lobby.sendMsg(LobbyMessage(LobbyMessageType.TEXTING).apply {
            data = LobbyMessageDataTexting(playerId, content)
        })
    }

    /**
     * 通过 Redis pub/sub 向大厅频道发送消息。
     *
     * @param msg 需要发布给大厅成员的消息对象
     */
    private suspend fun Lobby.sendMsg(msg: Any) {
        withContext(Dispatchers.IO) {
            // Redis 发布必须由调用方等待完成，保证同一次状态变更中的多条消息按锁内顺序发出。
            redisTemplate.convertAndSend(id.toString(), msg)
        }
    }

    /**
     * 在大厅级 Redis 锁内执行状态变更。
     *
     * @param lobbyId 用于生成锁名的大厅 ID
     * @param block 获取锁后执行的状态变更逻辑
     * @return 状态变更逻辑的返回值
     * @throws LobbyBusyException 当多次尝试仍无法获取大厅锁时抛出
     */
    private suspend fun <T> withLobbyLock(lobbyId: LobbyId, block: suspend () -> T): T {
        val lock = redissonClient.getLock("$LOCK_NAME_PREFIX$lobbyId")
        return withRedisLock(
            lock = lock,
            attempts = LOCK_ATTEMPTS,
            busyException = { LobbyBusyException(lobbyId) },
            block = block,
        )
    }

    /**
     * 同时持有玩家级和大厅级 Redis 锁后执行状态变更。
     *
     * @param playerId 用于生成玩家锁名的玩家 ID
     * @param lobbyId 用于生成大厅锁名的大厅 ID
     * @param block 获取锁后执行的状态变更逻辑
     * @return 状态变更逻辑的返回值
     * @throws LobbyBusyException 当多次尝试仍无法同时获取玩家锁和大厅锁时抛出
     */
    private suspend fun <T> withPlayerAndLobbyLock(
        playerId: SteamID64,
        lobbyId: LobbyId,
        block: suspend () -> T,
    ): T {
        val playerLock = redissonClient.getLock("$PLAYER_LOCK_NAME_PREFIX$playerId")
        val lobbyLock = redissonClient.getLock("$LOCK_NAME_PREFIX$lobbyId")
        val lock = redissonClient.getMultiLock(playerLock, lobbyLock)
        return withRedisLock(
            lock = lock,
            attempts = LOCK_ATTEMPTS,
            busyException = { LobbyBusyException("Player $playerId and lobby $lobbyId are busy") },
            block = block,
        )
    }

    /**
     * 广播大厅房主变更消息。
     *
     * @param newHost 新房主的 Steam ID64
     */
    private suspend fun Lobby.publishHostUpdate(newHost: SteamID64) {
        sendMsg(LobbyMessage(LobbyMessageType.UPDATE_HOST).apply {
            data = newHost
        })
    }

}

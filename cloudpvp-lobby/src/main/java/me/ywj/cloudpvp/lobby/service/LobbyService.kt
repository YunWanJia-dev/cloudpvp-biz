package me.ywj.cloudpvp.lobby.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.ywj.cloudpvp.core.model.lobby.LobbyMessage
import me.ywj.cloudpvp.core.model.lobby.LobbyMessageDataTexting
import me.ywj.cloudpvp.core.model.lobby.LobbyMessageType
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
        repeat(LOCK_ATTEMPTS) {
            // 退出接口不再接收 lobbyId，先读取当前索引以确定需要组合加锁的大厅。
            val playerLobbyOption = playerLobbyRepository.findById(playerId)
            if (!playerLobbyOption.isPresent) {
                return
            }
            val targetLobbyId = playerLobbyOption.get().lobbyId
            val completed = withPlayerAndLobbyLock(playerId, targetLobbyId) {
                // 加锁后复查索引，防止读取索引和拿锁之间玩家已经切换到其他大厅。
                val lockedPlayerLobbyOption = playerLobbyRepository.findById(playerId)
                if (!lockedPlayerLobbyOption.isPresent) {
                    return@withPlayerAndLobbyLock true
                }
                if (lockedPlayerLobbyOption.get().lobbyId != targetLobbyId) {
                    return@withPlayerAndLobbyLock false
                }

                val lobbyOption = lobbyRepository.findById(targetLobbyId)
                if (!lobbyOption.isPresent) {
                    // 退出接口保持幂等：索引指向的大厅已不存在时，只清理当前玩家索引。
                    playerLobbyRepository.deleteById(playerId)
                    return@withPlayerAndLobbyLock true
                }
                val lobby = lobbyOption.get()
                val removed = lobby.players!!.removeAll { it == playerId }
                if (!removed) {
                    // 退出接口保持幂等：玩家已不在成员列表时，只清理当前玩家索引。
                    playerLobbyRepository.deleteById(playerId)
                    return@withPlayerAndLobbyLock true
                }
                // 最后一名玩家离开时先广播销毁事件，让所有仍订阅该频道的 WebSocket 连接自清理。
                if (lobby.players!!.isEmpty()) {
                    lobby.sendMsg(LobbyMessage(LobbyMessageType.LOBBY_DESTROYED))
                    lobbyRepository.deleteById(targetLobbyId)
                    playerLobbyRepository.deleteById(playerId)
                    return@withPlayerAndLobbyLock true
                }
                lobby.sendMsg(LobbyMessage(LobbyMessageType.LEAVE).apply {
                    data = playerId
                })
                if (lobby.host == playerId) {
                    lobby.updateHost(lobby.players!![0])
                }
                lobbyRepository.save(lobby)
                playerLobbyRepository.deleteById(playerId)
                true
            }
            if (completed) {
                return
            }
        }
        throw LobbyBusyException("Player $playerId lobby mapping changed too frequently")
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
     * 更新大厅房主并广播房主变更消息。
     *
     * @param newHost 新房主的 Steam ID64
     */
    private suspend fun Lobby.updateHost(newHost: SteamID64) {
        this.host = newHost
        sendMsg(LobbyMessage(LobbyMessageType.UPDATE_HOST).apply {
            data = newHost
        })
    }

}

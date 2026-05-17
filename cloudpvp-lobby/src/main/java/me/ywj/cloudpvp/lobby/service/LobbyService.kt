package me.ywj.cloudpvp.lobby.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import me.ywj.cloudpvp.core.constant.lobby.LobbyConstant
import me.ywj.cloudpvp.core.model.lobby.LobbyMessage
import me.ywj.cloudpvp.core.model.lobby.LobbyMessageDataTexting
import me.ywj.cloudpvp.core.model.lobby.LobbyMessageType
import me.ywj.cloudpvp.core.type.LobbyId
import me.ywj.cloudpvp.core.type.SteamID64
import me.ywj.cloudpvp.core.utils.LobbyUtils
import me.ywj.cloudpvp.lobby.entity.Lobby
import me.ywj.cloudpvp.lobby.entity.LobbyPlayer
import me.ywj.cloudpvp.lobby.exceptions.LobbyBusyException
import me.ywj.cloudpvp.lobby.exceptions.LobbyNotExist
import me.ywj.cloudpvp.lobby.repository.LobbyRepository
import org.redisson.api.RFuture
import org.redisson.api.RedissonClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.listener.PatternTopic
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.seconds

/**
 * LobbyService
 *
 * @author sheip9
 * @since 2024/10/20 16:35
 */
@Service
class LobbyService @Autowired constructor(
    val lobbyRepository: LobbyRepository,
    val redisTemplate: RedisTemplate<String, Any>,
    val redissonClient: RedissonClient,
    val container: RedisMessageListenerContainer,
) {
    companion object {
        private const val LOCK_NAME_PREFIX = "LobbyLock:"
        private const val LOCK_WAIT_MILLIS = 200L
        private const val LOCK_WATCHDOG_LEASE_MILLIS = -1L
        private const val LOCK_ATTEMPTS = 5
        private const val CREATE_LOBBY_ATTEMPTS = 8
        private val LOCK_OWNER_ID = AtomicLong(1)
    }

    /**
     * 创建一个空大厅并返回大厅 ID。
     *
     * @return 新创建大厅的 ID
     * @throws LobbyBusyException 当多次生成 ID 后仍无法获得锁或完成创建时抛出
     */
    suspend fun createLobby(playerId: SteamID64): LobbyId {
        repeat(CREATE_LOBBY_ATTEMPTS) {
            val lobbyId = LobbyUtils.generateLobbyId()
            val createdLobbyId = withLobbyLock(lobbyId) {
                if (lobbyRepository.existsById(lobbyId)) {
                    null
                } else {
                    // 创建实体必须走 Repository，确保写入 @RedisHash("Lobby") 管理的 keyspace。
                    lobbyRepository.save(Lobby(lobbyId).apply {
                        host = playerId
                        players!!.add(playerId)
                    })
                    lobbyId
                }
            }
            if (createdLobbyId != null) {
                scheduleEmptyLobbyCleanup(createdLobbyId)
                return createdLobbyId
            }
        }
        throw LobbyBusyException("Unable to create lobby after $CREATE_LOBBY_ATTEMPTS attempts")
    }

    /**
     * 安排创建后无人加入的空大厅清理任务。
     *
     * @param lobbyId 需要在超时后检查的大厅 ID
     */
    private fun scheduleEmptyLobbyCleanup(lobbyId: LobbyId) {
        //特定时间过后 “创建房间”的玩家未能加入 则清理掉
        CoroutineScope(Dispatchers.Default).launch {
            delay((LobbyConstant.CREATE_TIMEOUT).seconds)
            try {
                withLobbyLock(lobbyId) {
                    val lobbyOption = lobbyRepository.findById(lobbyId)
                    if (!lobbyOption.isPresent) {
                        return@withLobbyLock
                    }
                    val lobby = lobbyOption.get()
                    if (lobby.players!!.isEmpty()) {
                        lobbyRepository.deleteById(lobbyId)
                    }
                }
            } catch (_: LobbyBusyException) {
                return@launch
            }
        }
    }

    /**
     * 通过 HTTP 将玩家加入目标大厅，并返回最新玩家列表。
     *
     * @param playerId 待加入大厅的玩家 ID
     * @param targetLobbyId 目标大厅 ID
     * @return 加入后的玩家 ID 列表
     * @throws LobbyNotExist 当目标大厅不存在时抛出
     * @throws LobbyBusyException 当目标大厅状态正被其他操作长期占用时抛出
     */
    suspend fun joinLobby(playerId: SteamID64, targetLobbyId: LobbyId): Lobby? {

        withLobbyLock(targetLobbyId) {
            val lobbyOption = lobbyRepository.findById(targetLobbyId)
            if (!lobbyOption.isPresent) {
                throw LobbyNotExist()
            }

            val lobby = lobbyOption.get()
            val players = lobby.players!!
            if (players.contains(playerId)) {
                return@withLobbyLock
            }
            players.add(playerId)
            lobbyRepository.save(lobby)

            lobby.sendMsg(LobbyMessage(LobbyMessageType.JOIN).apply {
                data = playerId
            })
        }

        return withContext(Dispatchers.IO) {
            lobbyRepository.findById(targetLobbyId)
        }.get()
    }

    /**
     * 通过 HTTP 将玩家从目标大厅移除，并在大厅为空时删除大厅。
     *
     * @param playerId 待离开大厅的玩家 ID
     * @param targetLobbyId 目标大厅 ID
     * @throws LobbyBusyException 当目标大厅状态正被其他操作长期占用时抛出
     */
    suspend fun leaveLobby(playerId: SteamID64, targetLobbyId: LobbyId) {
        withLobbyLock(targetLobbyId) {
            val lobbyOption = lobbyRepository.findById(targetLobbyId)
            if (!lobbyOption.isPresent) {
                return@withLobbyLock
            }
            val lobby = lobbyOption.get()
            val removed = lobby.players!!.removeAll { it == playerId }
            if (!removed) {
                return@withLobbyLock
            }
            if (lobby.players!!.isEmpty()) {
                lobbyRepository.deleteById(targetLobbyId)
                return@withLobbyLock
            }
            lobby.sendMsg(LobbyMessage(LobbyMessageType.LEAVE).apply {
                data = playerId
            })
            if (lobby.host == playerId) {
                lobby.updateHost(lobby.players!![0])
            }
            lobbyRepository.save(lobby)
        }
    }

    /**
     * 为当前 WebSocket 连接注册目标大厅的消息监听器。
     *
     * @param player 当前 socket 绑定的玩家连接状态
     * @param targetLobbyId 目标大厅 ID
     * @return true 表示玩家已在大厅内并完成监听注册
     * @throws LobbyNotExist 当目标大厅不存在时抛出
     * @throws LobbyBusyException 当目标大厅状态正被其他操作长期占用时抛出
     */
    suspend fun subscribeLobby(player: LobbyPlayer, targetLobbyId: LobbyId): Boolean {
        var playerList: ArrayList<Long>? = null
        val subscribed = withLobbyLock(targetLobbyId) {
            val lobbyOption = lobbyRepository.findById(targetLobbyId)
            if (!lobbyOption.isPresent) {
                throw LobbyNotExist()
            }

            val lobby = lobbyOption.get()
            if (!lobby.players!!.contains(player.steamID64)) {
                return@withLobbyLock false
            }
            container.addMessageListener(player.msgListener, PatternTopic(lobby.id.toString()))
            player.lobbyId = targetLobbyId
            playerList = ArrayList(lobby.players!!)
            true
        }

        if (subscribed) {
            player.sendMessage(LobbyMessage(LobbyMessageType.PLAYER_LIST).apply {
                data = playerList
            })
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
     * @param playerId 发送消息的玩家 ID
     * @param targetLobbyId 目标大厅 ID
     * @param content 文本消息内容
     * @throws LobbyNotExist 当目标大厅不存在时抛出
     * @throws LobbyBusyException 当目标大厅状态正被其他操作长期占用时抛出
     */
    suspend fun sendTextMessage(playerId: SteamID64, targetLobbyId: LobbyId, content: String) {
        withLobbyLock(targetLobbyId) {
            val lobbyOption = lobbyRepository.findById(targetLobbyId)
            if (!lobbyOption.isPresent) {
                throw LobbyNotExist()
            }
            val lobby = lobbyOption.get()
            if (!lobby.players!!.contains(playerId)) {
                return@withLobbyLock
            }
            lobby.sendMsg(LobbyMessage(LobbyMessageType.TEXTING).apply {
                data = LobbyMessageDataTexting(playerId, content)
            })
        }
    }

    /**
     * 通过 Redis pub/sub 向大厅频道发送消息。
     *
     * @param msg 需要发布给大厅成员的消息对象
     */
    fun Lobby.sendMsg(msg: Any) {
        CoroutineScope(Dispatchers.IO).launch {
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
        repeat(LOCK_ATTEMPTS) {
            // Redisson 异步锁的 ownerId 必须跨协程恢复保持稳定，不能依赖当前 JVM 线程 ID。
            val lockOwnerId = LOCK_OWNER_ID.getAndIncrement()
            val locked = withContext(NonCancellable) {
                // 锁归属结果必须在不可取消区间内确认；否则取消可能发生在 Redisson 已授权之后、finally 注册之前。
                lock.tryLockAsync(
                    LOCK_WAIT_MILLIS,
                    // 使用 Redisson watchdog 自动续期，避免固定短租约在 Repository 或监听器操作中途过期。
                    LOCK_WATCHDOG_LEASE_MILLIS,
                    TimeUnit.MILLISECONDS,
                    lockOwnerId,
                ).awaitValue()
            }

            if (!locked) {
                currentCoroutineContext().ensureActive()
                //没上锁成功就进入下一个循环
                return@repeat
            }

            return try {
                currentCoroutineContext().ensureActive()
                block()
            } finally {
                withContext(NonCancellable) {
                    lock.unlockAsync(lockOwnerId).awaitValue()
                }
            }

        }
        throw LobbyBusyException(lobbyId)
    }

    /**
     * 挂起等待 Redisson 异步结果完成。
     *
     * @return 异步操作完成后的结果
     */
    private suspend fun <T> RFuture<T>.awaitValue(): T {
        return suspendCancellableCoroutine { continuation ->
            whenComplete { value, throwable ->
                if (!continuation.isActive) {
                    return@whenComplete
                }
                if (throwable == null) {
                    continuation.resume(value)
                } else {
                    continuation.resumeWithException(throwable)
                }
            }
            continuation.invokeOnCancellation {
                cancel(false)
            }
        }
    }

    /**
     * 更新大厅房主并广播房主变更消息。
     *
     * @param newHost 新房主的 Steam ID64
     */
    fun Lobby.updateHost(newHost: SteamID64) {
        this.host = newHost
        sendMsg(LobbyMessage(LobbyMessageType.UPDATE_HOST).apply {
            data = newHost
        })
    }

}

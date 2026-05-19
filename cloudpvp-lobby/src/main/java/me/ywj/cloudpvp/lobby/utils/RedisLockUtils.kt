package me.ywj.cloudpvp.lobby.utils

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import me.ywj.cloudpvp.core.type.LobbyId
import me.ywj.cloudpvp.core.type.SteamID64
import me.ywj.cloudpvp.lobby.exceptions.LobbyBusyException
import org.redisson.api.RFuture
import org.redisson.api.RLock
import org.redisson.api.RedissonClient
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * RedisLockUtils
 * Redisson 协程锁工具。
 *
 * @author sheip9
 * @since 2026/5/17 17:50
 */
object RedisLockUtils {
    private const val LOBBY_LOCK_NAME_PREFIX = "LobbyLock:"
    private const val PLAYER_LOCK_NAME_PREFIX = "LobbyPlayerLock:"
    const val LOCK_ATTEMPTS = 5

    /**
     * 在大厅级 Redis 锁内执行状态变更。
     *
     * @param redissonClient Redisson 客户端
     * @param lobbyId 用于生成锁名的大厅 ID
     * @param block 获取锁后执行的状态变更逻辑
     * @return 状态变更逻辑的返回值
     * @throws LobbyBusyException 当多次尝试仍无法获取大厅锁时抛出
     */
    suspend fun <T> withLobbyLock(
        redissonClient: RedissonClient,
        lobbyId: LobbyId,
        block: suspend () -> T,
    ): T {
        val lock = redissonClient.getLock("$LOBBY_LOCK_NAME_PREFIX$lobbyId")
        return withRedisLock(
            lock = lock,
            attempts = LOCK_ATTEMPTS,
            busyException = { LobbyBusyException(lobbyId) },
            block = block,
        )
    }
    /**
     * 在玩家级和大厅级组合 Redis 锁内执行状态变更。
     *
     * @param redissonClient Redisson 客户端
     * @param playerId 用于生成玩家锁名的玩家 ID
     * @param lobbyId 用于生成大厅锁名的大厅 ID
     * @param block 获取锁后执行的状态变更逻辑
     * @return 状态变更逻辑的返回值
     * @throws LobbyBusyException 当多次尝试仍无法同时获取玩家锁和大厅锁时抛出
     */
    suspend fun <T> withPlayerAndLobbyLock(
        redissonClient: RedissonClient,
        playerId: SteamID64,
        lobbyId: LobbyId,
        block: suspend () -> T,
    ): T {
        val playerLock = redissonClient.getLock("$PLAYER_LOCK_NAME_PREFIX$playerId")
        val lobbyLock = redissonClient.getLock("$LOBBY_LOCK_NAME_PREFIX$lobbyId")
        val lock = redissonClient.getMultiLock(playerLock, lobbyLock)
        return withRedisLock(
            lock = lock,
            attempts = LOCK_ATTEMPTS,
            busyException = { LobbyBusyException("Player $playerId and lobby $lobbyId are busy") },
            block = block,
        )
    }

    private const val DEFAULT_LOCK_WAIT_MILLIS = 200L
    private const val LOCK_WATCHDOG_LEASE_MILLIS = -1L
    private val LOCK_OWNER_ID = AtomicLong(1)

    /**
     * 在指定 Redis 锁内执行状态变更。
     *
     * @param lock Redis 锁实例
     * @param attempts 获取锁重试次数
     * @param busyException 获取锁失败时需要抛出的异常
     * @param block 获取锁后执行的状态变更逻辑
     * @return 状态变更逻辑的返回值
     * @throws RuntimeException 当多次尝试仍无法获取锁时抛出
     */
    suspend fun <T> withRedisLock(
        lock: RLock,
        attempts: Int,
        busyException: () -> RuntimeException,
        block: suspend () -> T,
    ): T {
        repeat(attempts) {
            // Redisson 异步锁的 ownerId 必须跨协程恢复保持稳定，不能依赖当前 JVM 线程 ID。
            val lockOwnerId = LOCK_OWNER_ID.getAndIncrement()
            val locked = withContext(NonCancellable) {
                // 锁归属结果必须在不可取消区间内确认；否则取消可能发生在 Redisson 已授权之后、finally 注册之前。
                lock.tryLockAsync(
                    DEFAULT_LOCK_WAIT_MILLIS,
                    // 使用 Redisson watchdog 自动续期，避免固定短租约在 Repository 或监听器操作中途过期。
                    LOCK_WATCHDOG_LEASE_MILLIS,
                    TimeUnit.MILLISECONDS,
                    lockOwnerId,
                ).awaitValue()
            }

            if (!locked) {
                currentCoroutineContext().ensureActive()
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
        throw busyException()
    }

    /**
     * 挂起等待 Redisson 异步结果完成。
     *
     * @return 异步操作完成后的结果
     */
    suspend fun <T> RFuture<T>.awaitValue(): T {
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
}

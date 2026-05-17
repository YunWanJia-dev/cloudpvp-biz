package me.ywj.cloudpvp.lobby.utils

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.redisson.api.RFuture
import org.redisson.api.RLock
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

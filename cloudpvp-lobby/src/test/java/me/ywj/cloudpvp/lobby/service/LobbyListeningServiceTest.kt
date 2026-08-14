package me.ywj.cloudpvp.lobby.service

import me.ywj.cloudpvp.core.model.lobby.LobbyMessage
import me.ywj.cloudpvp.core.model.lobby.LobbyMessageType
import me.ywj.cloudpvp.core.model.lobby.LobbyStatus
import me.ywj.cloudpvp.lobby.entity.Lobby
import me.ywj.cloudpvp.lobby.model.messaging.LobbyUpdateMessage
import me.ywj.cloudpvp.lobby.repository.LobbyRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.argThat
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.redisson.api.RFuture
import org.redisson.api.RLock
import org.redisson.api.RedissonClient
import org.springframework.data.redis.core.RedisTemplate
import java.util.Optional
import java.util.concurrent.TimeUnit

/**
 * LobbyListeningServiceTest
 * 校验 Matcher 大厅状态更新对大厅状态与客户端广播的影响。
 *
 * @author sheip9
 * @since 2026/8/14 17:49
 */
class LobbyListeningServiceTest {
    /**
     * 验证退出匹配状态回传会恢复大厅并广播快照。
     */
    @Test
    fun waitingStatusUpdatesLobbyAndBroadcastsSnapshot() {
        val fixture = createFixture(LobbyStatus.MATCHING)

        fixture.service.consumeLobbyStatus(
            LobbyUpdateMessage("123", LobbyStatus.WAITING, "cancelled"),
        )

        assertThat(fixture.lobby.status).isEqualTo(LobbyStatus.WAITING)
        verify(fixture.lobbyRepository).save(fixture.lobby)
        verify(fixture.redisTemplate).convertAndSend(eq("123"), argThat { message ->
            message is LobbyMessage &&
                message.type == LobbyMessageType.LOBBY_SNAPSHOT &&
                message.data === fixture.lobby
        })
    }

    /**
     * 验证完整 Match 已关联后，迟到的 WAITING 不会清掉当前比赛。
     */
    @Test
    fun staleWaitingStatusDoesNotClearAssignedMatch() {
        val fixture = createFixture(LobbyStatus.MATCHING, matchId = "match-1")

        fixture.service.consumeLobbyStatus(
            LobbyUpdateMessage("123", LobbyStatus.WAITING, "stale"),
        )

        assertThat(fixture.lobby.status).isEqualTo(LobbyStatus.MATCHING)
        assertThat(fixture.lobby.matchId).isEqualTo("match-1")
        verify(fixture.lobbyRepository, never()).save(any(Lobby::class.java))
        verify(fixture.redisTemplate, never()).convertAndSend(any(String::class.java), any())
    }

    private fun createFixture(status: LobbyStatus, matchId: String? = null): Fixture {
        val lobbyRepository = mock(LobbyRepository::class.java)
        @Suppress("UNCHECKED_CAST")
        val redisTemplate = mock(RedisTemplate::class.java) as RedisTemplate<String, Any>
        val redissonClient = mock(RedissonClient::class.java)
        val lock = mock(RLock::class.java)
        val lobby = Lobby(123, arrayListOf(456L)).apply {
            this.status = status
            this.matchId = matchId
        }

        `when`(lobbyRepository.findById(123)).thenReturn(Optional.of(lobby))
        `when`(lobbyRepository.save(any(Lobby::class.java))).thenAnswer { it.getArgument(0) }
        `when`(redissonClient.getLock("LobbyLock:123")).thenReturn(lock)
        val lockFuture = completedFuture(true)
        val unlockFuture = completedFuture<Void?>(null)
        `when`(
            lock.tryLockAsync(
                anyLong(),
                anyLong(),
                eq(TimeUnit.MILLISECONDS),
                anyLong(),
            ),
        ).thenReturn(lockFuture)
        `when`(lock.unlockAsync(anyLong())).thenReturn(unlockFuture)

        return Fixture(
            service = LobbyListeningService(lobbyRepository, redisTemplate, redissonClient),
            lobbyRepository = lobbyRepository,
            redisTemplate = redisTemplate,
            lobby = lobby,
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> completedFuture(value: T): RFuture<T> {
        val future = mock(RFuture::class.java) as RFuture<T>
        `when`(future.whenComplete(any())).thenAnswer { invocation ->
            val consumer = invocation.getArgument<java.util.function.BiConsumer<T, Throwable?>>(0)
            consumer.accept(value, null)
            future
        }
        return future
    }

    private data class Fixture(
        val service: LobbyListeningService,
        val lobbyRepository: LobbyRepository,
        val redisTemplate: RedisTemplate<String, Any>,
        val lobby: Lobby,
    )
}

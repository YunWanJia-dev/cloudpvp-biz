package me.ywj.cloudpvp.lobby.service

import me.ywj.cloudpvp.core.model.lobby.LobbyMessage
import me.ywj.cloudpvp.core.model.lobby.LobbyMessageType
import me.ywj.cloudpvp.core.model.lobby.LobbyStatus
import me.ywj.cloudpvp.lobby.entity.Lobby
import me.ywj.cloudpvp.lobby.model.messaging.MatchMessage
import me.ywj.cloudpvp.lobby.model.messaging.MatchmakingMatchStatus
import me.ywj.cloudpvp.lobby.model.messaging.MatchmakingMember
import me.ywj.cloudpvp.lobby.model.messaging.MatchmakingServer
import me.ywj.cloudpvp.lobby.model.messaging.MatchmakingTeam
import me.ywj.cloudpvp.lobby.repository.LobbyRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.argThat
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.redisson.api.RFuture
import org.redisson.api.RLock
import org.redisson.api.RedissonClient
import org.springframework.data.redis.core.RedisTemplate
import java.util.Optional
import java.util.concurrent.TimeUnit

/**
 * MatchListeningServiceTest
 * 校验完整 Match 生命周期对大厅状态与客户端广播的影响。
 *
 * @author sheip9
 * @since 2026/8/14 17:49
 */
class MatchListeningServiceTest {
    /**
     * 验证 WAITING_FOR_SERVER 关联比赛后仍保持 MATCHING 并回传完整 Match。
     */
    @Test
    fun waitingForServerUpdatesLobbyAndBroadcastsMatchSuccess() {
        val fixture = createFixture(LobbyStatus.MATCHING)
        val match = matchMessage(MatchmakingMatchStatus.WAITING_FOR_SERVER)

        fixture.service.consumeMatch(match)

        assertThat(fixture.lobby.status).isEqualTo(LobbyStatus.MATCHING)
        assertThat(fixture.lobby.matchId).isEqualTo("match-1")
        verify(fixture.lobbyRepository).save(fixture.lobby)
        verifyPublished(fixture, LobbyMessageType.MATCH_SUCCESS, match)
    }

    /**
     * 验证 IN_PROGRESS 将大厅推进为 IN_MATCH 并广播服务器就绪事件。
     */
    @Test
    fun inProgressUpdatesLobbyToInMatch() {
        val fixture = waitingForServerFixture()
        clearInvocations(fixture.lobbyRepository, fixture.redisTemplate)
        val match = matchMessage(
            status = MatchmakingMatchStatus.IN_PROGRESS,
            server = MatchmakingServer("127.0.0.1"),
        )

        fixture.service.consumeMatch(match)

        assertThat(fixture.lobby.status).isEqualTo(LobbyStatus.IN_MATCH)
        assertThat(fixture.lobby.matchId).isEqualTo("match-1")
        verify(fixture.lobbyRepository).save(fixture.lobby)
        verifyPublished(fixture, LobbyMessageType.GAME_CONFIRMED, match)
    }

    /**
     * 验证 IN_PROGRESS 重投不重复保存，但仍重播客户端事件以覆盖上次发布失败。
     */
    @Test
    fun duplicateInProgressRebroadcastsWithoutSavingAgain() {
        val fixture = waitingForServerFixture()
        val match = matchMessage(
            status = MatchmakingMatchStatus.IN_PROGRESS,
            server = MatchmakingServer("127.0.0.1"),
        )
        fixture.service.consumeMatch(match)
        clearInvocations(fixture.lobbyRepository, fixture.redisTemplate)

        fixture.service.consumeMatch(match)

        verify(fixture.lobbyRepository, never()).save(any(Lobby::class.java))
        verifyPublished(fixture, LobbyMessageType.GAME_CONFIRMED, match)
    }

    /**
     * 验证更旧的 WAITING_FOR_SERVER 不会回退已进行中的比赛。
     */
    @Test
    fun staleWaitingForServerDoesNotRegressInProgressMatch() {
        val fixture = waitingForServerFixture()
        fixture.service.consumeMatch(
            matchMessage(
                status = MatchmakingMatchStatus.IN_PROGRESS,
                server = MatchmakingServer("127.0.0.1"),
            ),
        )
        clearInvocations(fixture.lobbyRepository, fixture.redisTemplate)

        fixture.service.consumeMatch(matchMessage(MatchmakingMatchStatus.WAITING_FOR_SERVER))

        assertThat(fixture.lobby.status).isEqualTo(LobbyStatus.IN_MATCH)
        assertThat(fixture.lobby.matchId).isEqualTo("match-1")
        verify(fixture.lobbyRepository, never()).save(any(Lobby::class.java))
        verify(fixture.redisTemplate, never()).convertAndSend(any(String::class.java), any())
    }

    /**
     * 验证 IN_PROGRESS 缺少服务器地址时不会推进状态或广播。
     */
    @Test
    fun inProgressWithoutServerIsIgnored() {
        val fixture = waitingForServerFixture()
        clearInvocations(fixture.lobbyRepository, fixture.redisTemplate)

        fixture.service.consumeMatch(
            matchMessage(MatchmakingMatchStatus.IN_PROGRESS),
        )

        assertThat(fixture.lobby.status).isEqualTo(LobbyStatus.MATCHING)
        assertThat(fixture.lobby.matchId).isEqualTo("match-1")
        verify(fixture.lobbyRepository, never()).save(any(Lobby::class.java))
        verify(fixture.redisTemplate, never()).convertAndSend(any(String::class.java), any())
    }

    /**
     * 验证多支队伍中重复 lobbyId 只会消费一次。
     */
    @Test
    fun duplicateLobbyIdsAcrossTeamsAreConsumedOnce() {
        val fixture = createFixture(LobbyStatus.MATCHING)
        val match = matchMessage(MatchmakingMatchStatus.WAITING_FOR_SERVER).copy(
            teams = listOf(
                MatchmakingTeam(listOf("123"), listOf(MatchmakingMember("456"))),
                MatchmakingTeam(listOf("123"), listOf(MatchmakingMember("789"))),
            ),
        )

        fixture.service.consumeMatch(match)

        verify(fixture.lobbyRepository, times(1)).findById(123)
        verify(fixture.lobbyRepository, times(1)).save(fixture.lobby)
        verifyPublished(fixture, LobbyMessageType.MATCH_SUCCESS, match)
    }

    private fun waitingForServerFixture(): Fixture {
        val fixture = createFixture(LobbyStatus.MATCHING)
        fixture.service.consumeMatch(matchMessage(MatchmakingMatchStatus.WAITING_FOR_SERVER))
        return fixture
    }

    private fun matchMessage(
        status: MatchmakingMatchStatus,
        server: MatchmakingServer? = null,
    ): MatchMessage {
        return MatchMessage(
            matchId = "match-1",
            gameMode = "CS2/5v5/competitive",
            status = status,
            teams = listOf(
                MatchmakingTeam(
                    lobbyIds = listOf("123"),
                    members = listOf(MatchmakingMember("456")),
                ),
            ),
            server = server,
            createdAt = "2026-08-13T08:00:00Z",
            updatedAt = "2026-08-13T08:00:00Z",
        )
    }

    private fun createFixture(status: LobbyStatus): Fixture {
        val lobbyRepository = mock(LobbyRepository::class.java)
        @Suppress("UNCHECKED_CAST")
        val redisTemplate = mock(RedisTemplate::class.java) as RedisTemplate<String, Any>
        val redissonClient = mock(RedissonClient::class.java)
        val lock = mock(RLock::class.java)
        val lobby = Lobby(123, arrayListOf(456L)).apply { this.status = status }

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
            service = MatchListeningService(lobbyRepository, redisTemplate, redissonClient),
            lobbyRepository = lobbyRepository,
            redisTemplate = redisTemplate,
            lobby = lobby,
        )
    }

    private fun verifyPublished(fixture: Fixture, type: LobbyMessageType, data: Any) {
        verify(fixture.redisTemplate).convertAndSend(eq("123"), argThat { message ->
            message is LobbyMessage && message.type == type && message.data === data
        })
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
        val service: MatchListeningService,
        val lobbyRepository: LobbyRepository,
        val redisTemplate: RedisTemplate<String, Any>,
        val lobby: Lobby,
    )
}

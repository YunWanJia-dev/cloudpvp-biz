package me.ywj.cloudpvp.lobby.service

import me.ywj.cloudpvp.core.model.lobby.LobbyMessage
import me.ywj.cloudpvp.core.model.lobby.LobbyMessageType
import me.ywj.cloudpvp.core.model.lobby.LobbyStatus
import me.ywj.cloudpvp.lobby.entity.Lobby
import me.ywj.cloudpvp.lobby.entity.Match
import me.ywj.cloudpvp.lobby.entity.MatchMember
import me.ywj.cloudpvp.lobby.entity.MatchServer
import me.ywj.cloudpvp.lobby.entity.MatchStatus
import me.ywj.cloudpvp.lobby.entity.MatchTeam
import me.ywj.cloudpvp.lobby.model.messaging.LobbyUpdateMessage
import me.ywj.cloudpvp.lobby.repository.LobbyRepository
import me.ywj.cloudpvp.lobby.repository.MatchRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.argThat
import org.mockito.ArgumentMatchers.eq
import org.mockito.ArgumentMatchers.same
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.redisson.api.RFuture
import org.redisson.api.RLock
import org.redisson.api.RedissonClient
import org.springframework.data.redis.connection.Message
import org.springframework.data.redis.connection.MessageListener
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.data.redis.listener.RedisMessageListenerContainer
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

    /**
     * 验证订阅比赛时使用精确频道，并立即回放已保存的等待服务器快照。
     */
    @Test
    fun ensureMatchSubscriptionReplaysWaitingForServerMatch() {
        val match = matchSnapshot(MatchStatus.WAITING_FOR_SERVER)
        val fixture = createFixture(LobbyStatus.MATCHING, match = match)

        fixture.service.ensureMatchSubscription(match.matchId)

        assertThat(fixture.lobby.matchId).isEqualTo(match.matchId)
        verify(fixture.container).addMessageListener(
            any(MessageListener::class.java),
            eq(ChannelTopic(match.matchId)),
        )
        verify(fixture.lobbyRepository).save(fixture.lobby)
        verifyPublished(fixture, LobbyMessageType.MATCH_SUCCESS, match)
    }

    /**
     * 验证比赛更新通知会读取最新快照、推进大厅状态，并在进入比赛后取消订阅。
     */
    @Test
    fun inProgressNotificationUpdatesLobbyAndRemovesSubscription() {
        val waitingMatch = matchSnapshot(MatchStatus.WAITING_FOR_SERVER)
        val fixture = createFixture(LobbyStatus.MATCHING, match = waitingMatch)
        fixture.service.ensureMatchSubscription(waitingMatch.matchId)
        val listenerCaptor = ArgumentCaptor.forClass(MessageListener::class.java)
        verify(fixture.container).addMessageListener(
            listenerCaptor.capture(),
            eq(ChannelTopic(waitingMatch.matchId)),
        )

        val inProgressMatch = matchSnapshot(
            status = MatchStatus.IN_PROGRESS,
            server = MatchServer("127.0.0.1"),
            updatedAt = "2026-08-17T09:00:10Z",
        )
        `when`(fixture.matchRepository.findById(waitingMatch.matchId)).thenReturn(Optional.of(inProgressMatch))
        clearInvocations(fixture.lobbyRepository, fixture.redisTemplate, fixture.container)

        listenerCaptor.value.onMessage(mock(Message::class.java), null)

        assertThat(fixture.lobby.status).isEqualTo(LobbyStatus.IN_MATCH)
        verify(fixture.lobbyRepository).save(fixture.lobby)
        verifyPublished(fixture, LobbyMessageType.GAME_CONFIRMED, inProgressMatch)
        verify(fixture.container).removeMessageListener(
            same(listenerCaptor.value),
            eq(ChannelTopic(waitingMatch.matchId)),
        )
    }

    /**
     * 验证同一比赛重复确保订阅时不会重复注册 Redis Listener。
     */
    @Test
    fun duplicateEnsureMatchSubscriptionAddsListenerOnce() {
        val match = matchSnapshot(MatchStatus.WAITING_FOR_SERVER)
        val fixture = createFixture(LobbyStatus.MATCHING, match = match)

        fixture.service.ensureMatchSubscription(match.matchId)
        fixture.service.ensureMatchSubscription(match.matchId)

        verify(fixture.container, times(1)).addMessageListener(
            any(MessageListener::class.java),
            eq(ChannelTopic(match.matchId)),
        )
    }

    /**
     * 验证 Redis SUBSCRIBE 注册失败时会补偿移除容器中可能残留的半注册 Listener。
     */
    @Test
    fun failedMatchSubscriptionRegistrationRemovesPartialListener() {
        val match = matchSnapshot(MatchStatus.WAITING_FOR_SERVER)
        val fixture = createFixture(LobbyStatus.MATCHING, match = match)
        val listenerCaptor = ArgumentCaptor.forClass(MessageListener::class.java)
        doThrow(IllegalStateException("subscribe failed")).`when`(fixture.container).addMessageListener(
            listenerCaptor.capture(),
            eq(ChannelTopic(match.matchId)),
        )

        assertThatThrownBy { fixture.service.ensureMatchSubscription(match.matchId) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("subscribe failed")

        verify(fixture.container).removeMessageListener(
            same(listenerCaptor.value),
            eq(ChannelTopic(match.matchId)),
        )
        verify(fixture.matchRepository, never()).findById(match.matchId)
    }

    private fun createFixture(
        status: LobbyStatus,
        matchId: String? = null,
        match: Match? = null,
    ): Fixture {
        val lobbyRepository = mock(LobbyRepository::class.java)
        val matchRepository = mock(MatchRepository::class.java)
        @Suppress("UNCHECKED_CAST")
        val redisTemplate = mock(RedisTemplate::class.java) as RedisTemplate<String, Any>
        val redissonClient = mock(RedissonClient::class.java)
        val container = mock(RedisMessageListenerContainer::class.java)
        val lock = mock(RLock::class.java)
        val lobby = Lobby(123, arrayListOf(456L)).apply {
            this.status = status
            this.matchId = matchId
        }

        `when`(lobbyRepository.findById(123)).thenReturn(Optional.of(lobby))
        `when`(lobbyRepository.save(any(Lobby::class.java))).thenAnswer { it.getArgument(0) }
        match?.let {
            `when`(matchRepository.findById(it.matchId)).thenReturn(Optional.of(it))
        }
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
            service = LobbyListeningService(
                lobbyRepository,
                matchRepository,
                redisTemplate,
                redissonClient,
                container,
            ),
            lobbyRepository = lobbyRepository,
            matchRepository = matchRepository,
            redisTemplate = redisTemplate,
            container = container,
            lobby = lobby,
        )
    }

    private fun matchSnapshot(
        status: MatchStatus,
        server: MatchServer? = null,
        updatedAt: String = "2026-08-17T09:00:00Z",
    ): Match {
        return Match(
            matchId = "match-1",
            gameMode = "CS2/5v5/competitive",
            status = status,
            teams = listOf(
                MatchTeam(
                    lobbyIds = listOf("123"),
                    members = listOf(MatchMember("456")),
                ),
            ),
            server = server,
            createdAt = "2026-08-17T09:00:00Z",
            updatedAt = updatedAt,
        )
    }

    private fun verifyPublished(fixture: Fixture, type: LobbyMessageType, match: Match) {
        verify(fixture.redisTemplate).convertAndSend(eq("123"), argThat { message ->
            message is LobbyMessage && message.type == type && message.data === match
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
        val service: LobbyListeningService,
        val lobbyRepository: LobbyRepository,
        val matchRepository: MatchRepository,
        val redisTemplate: RedisTemplate<String, Any>,
        val container: RedisMessageListenerContainer,
        val lobby: Lobby,
    )
}

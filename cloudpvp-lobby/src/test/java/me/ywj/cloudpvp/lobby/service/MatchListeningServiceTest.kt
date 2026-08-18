package me.ywj.cloudpvp.lobby.service

import me.ywj.cloudpvp.lobby.entity.Match
import me.ywj.cloudpvp.lobby.entity.MatchMember
import me.ywj.cloudpvp.lobby.entity.MatchServer
import me.ywj.cloudpvp.lobby.entity.MatchStatus
import me.ywj.cloudpvp.lobby.entity.MatchTeam
import me.ywj.cloudpvp.lobby.model.messaging.MatchMessage
import me.ywj.cloudpvp.lobby.model.messaging.MatchmakingMatchStatus
import me.ywj.cloudpvp.lobby.model.messaging.MatchmakingMember
import me.ywj.cloudpvp.lobby.model.messaging.MatchmakingServer
import me.ywj.cloudpvp.lobby.model.messaging.MatchmakingTeam
import me.ywj.cloudpvp.lobby.repository.MatchRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.argThat
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.data.redis.core.RedisTemplate
import java.util.Optional

/**
 * MatchListeningServiceTest
 * 校验比赛消息持久化、Match 频道订阅注册与 Redis 广播行为。
 *
 * @author sheip9
 * @since 2026/8/14 17:49
 */
class MatchListeningServiceTest {
    /**
     * 验证 match.create 会保存完整实体，再确保订阅并向原始 Match ID 频道发布。
     */
    @Test
    fun waitingForServerIsStoredSubscribedAndPublished() {
        val fixture = createFixture()
        val message = matchMessage(MatchmakingMatchStatus.WAITING_FOR_SERVER)
        val expected = match(MatchStatus.WAITING_FOR_SERVER)

        fixture.service.consumeMatch(message)

        val saved = ArgumentCaptor.forClass(Match::class.java)
        val ordered = inOrder(fixture.matchRepository, fixture.lobbyListeningService, fixture.redisTemplate)
        ordered.verify(fixture.matchRepository).save(saved.capture())
        ordered.verify(fixture.lobbyListeningService).ensureMatchSubscription(MATCH_ID)
        ordered.verify(fixture.redisTemplate).convertAndSend(eq(MATCH_ID), argThat { published ->
            published is Match && published == expected
        })
        assertThat(saved.value).isEqualTo(expected)
    }

    /**
     * 验证更新为 IN_PROGRESS 时会保存服务器信息并重新确保订阅、发布最新实体。
     */
    @Test
    fun inProgressUpdateIsStoredSubscribedAndPublished() {
        val fixture = createFixture(match(MatchStatus.WAITING_FOR_SERVER))
        val message = matchMessage(
            status = MatchmakingMatchStatus.IN_PROGRESS,
            server = MatchmakingServer(SERVER_IP),
            updatedAt = UPDATED_AT,
        )
        val expected = match(
            status = MatchStatus.IN_PROGRESS,
            server = MatchServer(SERVER_IP),
            updatedAt = UPDATED_AT,
        )

        fixture.service.consumeMatch(message)

        val saved = ArgumentCaptor.forClass(Match::class.java)
        val ordered = inOrder(fixture.matchRepository, fixture.lobbyListeningService, fixture.redisTemplate)
        ordered.verify(fixture.matchRepository).save(saved.capture())
        ordered.verify(fixture.lobbyListeningService).ensureMatchSubscription(MATCH_ID)
        ordered.verify(fixture.redisTemplate).convertAndSend(eq(MATCH_ID), argThat { published ->
            published is Match && published == expected
        })
        assertThat(saved.value).isEqualTo(expected)
    }

    /**
     * 验证相同消息重投不重复保存，但仍补偿订阅注册和 Redis 发布。
     */
    @Test
    fun duplicateMessageEnsuresSubscriptionAndPublishesWithoutSavingAgain() {
        val fixture = createFixture()
        val message = matchMessage(MatchmakingMatchStatus.WAITING_FOR_SERVER)
        val expected = match(MatchStatus.WAITING_FOR_SERVER)
        fixture.service.consumeMatch(message)
        clearInvocations(fixture.matchRepository, fixture.lobbyListeningService, fixture.redisTemplate)

        fixture.service.consumeMatch(message)

        verify(fixture.matchRepository, never()).save(any(Match::class.java))
        verify(fixture.lobbyListeningService).ensureMatchSubscription(MATCH_ID)
        verifyPublished(fixture, expected)
    }

    /**
     * 验证迟到的 WAITING_FOR_SERVER 不会覆盖已经进行中的比赛。
     */
    @Test
    fun staleWaitingForServerDoesNotOverwriteInProgressMatch() {
        val existing = match(
            status = MatchStatus.IN_PROGRESS,
            server = MatchServer(SERVER_IP),
            updatedAt = UPDATED_AT,
        )
        val fixture = createFixture(existing)

        fixture.service.consumeMatch(
            matchMessage(
                status = MatchmakingMatchStatus.WAITING_FOR_SERVER,
                updatedAt = CREATED_AT,
            ),
        )

        verifyIgnored(fixture)
    }

    /**
     * 验证缺少服务器信息的 IN_PROGRESS 消息不会被保存、订阅或发布。
     */
    @Test
    fun inProgressWithoutServerIsIgnored() {
        val fixture = createFixture(match(MatchStatus.WAITING_FOR_SERVER))

        fixture.service.consumeMatch(
            matchMessage(
                status = MatchmakingMatchStatus.IN_PROGRESS,
                updatedAt = UPDATED_AT,
            ),
        )

        verifyIgnored(fixture)
    }

    /**
     * 验证相同 updatedAt 却内容冲突的消息不会覆盖现有比赛。
     */
    @Test
    fun conflictingMessageWithSameUpdatedAtIsIgnored() {
        val fixture = createFixture(
            match(
                status = MatchStatus.WAITING_FOR_SERVER,
                updatedAt = UPDATED_AT,
            ),
        )

        fixture.service.consumeMatch(
            matchMessage(
                status = MatchmakingMatchStatus.IN_PROGRESS,
                server = MatchmakingServer(SERVER_IP),
                updatedAt = UPDATED_AT,
            ),
        )

        verifyIgnored(fixture)
    }

    private fun matchMessage(
        status: MatchmakingMatchStatus,
        server: MatchmakingServer? = null,
        updatedAt: String = CREATED_AT,
    ): MatchMessage {
        return MatchMessage(
            matchId = MATCH_ID,
            gameMode = GAME_MODE,
            status = status,
            teams = listOf(
                MatchmakingTeam(
                    lobbyIds = listOf(LOBBY_ID),
                    members = listOf(MatchmakingMember(PLAYER_ID)),
                ),
            ),
            server = server,
            createdAt = CREATED_AT,
            updatedAt = updatedAt,
        )
    }

    private fun match(
        status: MatchStatus,
        server: MatchServer? = null,
        updatedAt: String = CREATED_AT,
    ): Match {
        return Match(
            matchId = MATCH_ID,
            gameMode = GAME_MODE,
            status = status,
            teams = listOf(
                MatchTeam(
                    lobbyIds = listOf(LOBBY_ID),
                    members = listOf(MatchMember(PLAYER_ID)),
                ),
            ),
            server = server,
            createdAt = CREATED_AT,
            updatedAt = updatedAt,
        )
    }

    private fun createFixture(existing: Match? = null): Fixture {
        val matchRepository = mock(MatchRepository::class.java)
        @Suppress("UNCHECKED_CAST")
        val redisTemplate = mock(RedisTemplate::class.java) as RedisTemplate<String, Any>
        val lobbyListeningService = mock(LobbyListeningService::class.java)
        var storedMatch = existing

        `when`(matchRepository.findById(anyString())).thenAnswer { Optional.ofNullable(storedMatch) }
        `when`(matchRepository.save(any(Match::class.java))).thenAnswer { invocation ->
            invocation.getArgument<Match>(0).also { storedMatch = it }
        }

        return Fixture(
            service = MatchListeningService(matchRepository, redisTemplate, lobbyListeningService),
            matchRepository = matchRepository,
            redisTemplate = redisTemplate,
            lobbyListeningService = lobbyListeningService,
        )
    }

    private fun verifyPublished(fixture: Fixture, expected: Match) {
        verify(fixture.redisTemplate).convertAndSend(eq(MATCH_ID), argThat { published ->
            published is Match && published == expected
        })
    }

    private fun verifyIgnored(fixture: Fixture) {
        verify(fixture.matchRepository, never()).save(any(Match::class.java))
        verify(fixture.lobbyListeningService, never()).ensureMatchSubscription(anyString())
        verify(fixture.redisTemplate, never()).convertAndSend(anyString(), any())
    }

    private data class Fixture(
        val service: MatchListeningService,
        val matchRepository: MatchRepository,
        val redisTemplate: RedisTemplate<String, Any>,
        val lobbyListeningService: LobbyListeningService,
    )

    private companion object {
        const val MATCH_ID = "match-1"
        const val LOBBY_ID = "123"
        const val PLAYER_ID = "456"
        const val GAME_MODE = "CS2/5v5/competitive"
        const val SERVER_IP = "127.0.0.1"
        const val CREATED_AT = "2026-08-13T08:00:00Z"
        const val UPDATED_AT = "2026-08-13T08:00:10Z"
    }
}

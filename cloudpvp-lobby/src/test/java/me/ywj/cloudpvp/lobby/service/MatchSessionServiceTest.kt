package me.ywj.cloudpvp.lobby.service

import me.ywj.cloudpvp.lobby.entity.Match
import me.ywj.cloudpvp.lobby.entity.MatchMember
import me.ywj.cloudpvp.lobby.entity.MatchStatus
import me.ywj.cloudpvp.lobby.entity.MatchTeam
import me.ywj.cloudpvp.lobby.repository.MatchRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.data.redis.connection.MessageListener
import org.springframework.data.redis.listener.PatternTopic
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import java.util.Optional

/**
 * MatchSessionServiceTest
 * 校验比赛 WebSocket 会话的成员鉴权、订阅和退订行为。
 *
 * @author sheip9
 * @since 2026/8/20 16:57
 */
class MatchSessionServiceTest {
    @Test
    fun matchPlayerCanSubscribeAndUnsubscribe() {
        val matchRepository = mock(MatchRepository::class.java)
        val container = mock(RedisMessageListenerContainer::class.java)
        val match = createMatch()
        `when`(matchRepository.findById("match-1")).thenReturn(Optional.of(match))
        val service = MatchSessionService(matchRepository, container)

        val subscribed = service.trySubscribe(
            playerId = 76561197960265729,
            matchId = "match-1",
        ) {}

        assertThat(subscribed).isTrue()
        val listenerCaptor = ArgumentCaptor.forClass(MessageListener::class.java)
        verify(container).addMessageListener(listenerCaptor.capture(), eq(PatternTopic("match-1")))

        service.unsubscribe(76561197960265729)

        verify(container).removeMessageListener(listenerCaptor.value)
    }

    @Test
    fun playerOutsideMatchCannotSubscribe() {
        val matchRepository = mock(MatchRepository::class.java)
        val container = mock(RedisMessageListenerContainer::class.java)
        `when`(matchRepository.findById("match-1")).thenReturn(Optional.of(createMatch()))
        val service = MatchSessionService(matchRepository, container)

        val subscribed = service.trySubscribe(
            playerId = 76561197960265730,
            matchId = "match-1",
        ) {}

        assertThat(subscribed).isFalse()
        verify(container, never()).addMessageListener(any(MessageListener::class.java), any(PatternTopic::class.java))
    }

    private fun createMatch(): Match {
        return Match(
            matchId = "match-1",
            gameMode = "CS2/5v5/competitive",
            status = MatchStatus.WAITING_FOR_SERVER,
            teams = listOf(
                MatchTeam(
                    lobbyIds = listOf("123"),
                    members = listOf(MatchMember("76561197960265729")),
                ),
            ),
            createdAt = "2026-08-13T08:00:00Z",
            updatedAt = "2026-08-13T08:00:00Z",
        )
    }
}

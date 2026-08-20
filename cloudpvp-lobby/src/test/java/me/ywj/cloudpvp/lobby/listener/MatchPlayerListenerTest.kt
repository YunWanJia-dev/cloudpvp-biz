package me.ywj.cloudpvp.lobby.listener

import me.ywj.cloudpvp.lobby.entity.Match
import me.ywj.cloudpvp.lobby.entity.MatchMember
import me.ywj.cloudpvp.lobby.entity.MatchStatus
import me.ywj.cloudpvp.lobby.entity.MatchTeam
import me.ywj.cloudpvp.lobby.repository.MatchRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.data.redis.connection.DefaultMessage
import java.util.Optional

/**
 * MatchPlayerListenerTest
 * 校验比赛变更事件会触发最新完整比赛快照查询与发送。
 *
 * @author sheip9
 * @since 2026/8/20 16:57
 */
class MatchPlayerListenerTest {
    @Test
    fun redisEventQueriesAndSendsLatestMatch() {
        val matchRepository = mock(MatchRepository::class.java)
        val match = createMatch()
        `when`(matchRepository.findById("match-1")).thenReturn(Optional.of(match))
        var sentMatch: Match? = null
        val listener = MatchPlayerListener("match-1", matchRepository) { sentMatch = it }

        listener.onMessage(DefaultMessage("match-1".toByteArray(), byteArrayOf()), null)

        assertThat(sentMatch).isEqualTo(match)
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

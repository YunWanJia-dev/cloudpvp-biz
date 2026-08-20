package me.ywj.cloudpvp.lobby.service

import me.ywj.cloudpvp.lobby.entity.Match
import me.ywj.cloudpvp.lobby.entity.MatchMember
import me.ywj.cloudpvp.lobby.entity.MatchStatus
import me.ywj.cloudpvp.lobby.entity.MatchTeam
import me.ywj.cloudpvp.lobby.model.messaging.MatchMessage
import me.ywj.cloudpvp.lobby.model.messaging.MatchmakingMatchStatus
import me.ywj.cloudpvp.lobby.model.messaging.MatchmakingMember
import me.ywj.cloudpvp.lobby.model.messaging.MatchmakingTeam
import me.ywj.cloudpvp.lobby.repository.MatchRepository
import org.junit.jupiter.api.Test
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.data.redis.core.RedisTemplate

class MatchListeningServiceTest {
    @Test
    fun messageIsStoredThenBroadcast() {
        val matchRepository = mock(MatchRepository::class.java)
        @Suppress("UNCHECKED_CAST")
        val redisTemplate = mock(RedisTemplate::class.java) as RedisTemplate<String, Any>
        val message = MatchMessage(
            matchId = "match-1",
            gameMode = "CS2/5v5/competitive",
            status = MatchmakingMatchStatus.WAITING_FOR_SERVER,
            teams = listOf(
                MatchmakingTeam(
                    lobbyIds = listOf("123"),
                    members = listOf(MatchmakingMember("456")),
                ),
            ),
            server = null,
            createdAt = "2026-08-13T08:00:00Z",
            updatedAt = "2026-08-13T08:00:00Z",
        )

        val match = Match(
            matchId = "match-1",
            gameMode = "CS2/5v5/competitive",
            status = MatchStatus.WAITING_FOR_SERVER,
            teams = listOf(
                MatchTeam(
                    lobbyIds = listOf("123"),
                    members = listOf(MatchMember("456")),
                ),
            ),
            server = null,
            createdAt = "2026-08-13T08:00:00Z",
            updatedAt = "2026-08-13T08:00:00Z",
        )
        `when`(matchRepository.save(match)).thenReturn(match)

        MatchListeningService(matchRepository, redisTemplate).consumeMatch(message)

        val ordered = inOrder(matchRepository, redisTemplate)
        ordered.verify(matchRepository).save(match)
        ordered.verify(redisTemplate).convertAndSend("match-1", "match-1")
    }
}

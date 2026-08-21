package me.ywj.cloudpvp.lobby.service

import me.ywj.cloudpvp.core.model.lobby.LobbyStatus
import me.ywj.cloudpvp.lobby.entity.Lobby
import me.ywj.cloudpvp.lobby.model.messaging.LobbyUpdateMessage
import me.ywj.cloudpvp.lobby.model.publishing.LobbyMessage
import me.ywj.cloudpvp.lobby.model.publishing.LobbyMessageType
import me.ywj.cloudpvp.lobby.repository.LobbyRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.data.redis.core.RedisTemplate
import java.util.Optional

class LobbyListeningServiceTest {
    @Test
    fun updateIsStoredAndBroadcast() {
        val lobbyRepository = mock(LobbyRepository::class.java)
        @Suppress("UNCHECKED_CAST")
        val redisTemplate = mock(RedisTemplate::class.java) as RedisTemplate<String, Any>
        val lobby = Lobby(123).apply { status = LobbyStatus.MATCHING }
        `when`(lobbyRepository.findById(123)).thenReturn(Optional.of(lobby))

        LobbyListeningService(lobbyRepository, redisTemplate).consumeLobbyStatus(
            LobbyUpdateMessage("123", LobbyStatus.WAITING, "match-1"),
        )

        assertThat(lobby.status).isEqualTo(LobbyStatus.WAITING)
        assertThat(lobby.matchId).isEqualTo("match-1")
        verify(lobbyRepository).save(lobby)
        verify(redisTemplate).convertAndSend(
            "123",
            LobbyMessage(LobbyMessageType.SHOULD_SYNC, null, ""),
        )
    }
}

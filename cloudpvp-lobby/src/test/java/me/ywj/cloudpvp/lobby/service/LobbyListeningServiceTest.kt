package me.ywj.cloudpvp.lobby.service

import me.ywj.cloudpvp.core.model.lobby.LobbyStatus
import me.ywj.cloudpvp.lobby.entity.Lobby
import me.ywj.cloudpvp.lobby.model.messaging.LobbyUpdateMessage
import me.ywj.cloudpvp.lobby.model.publishing.LobbyMessage
import me.ywj.cloudpvp.lobby.model.publishing.LobbyMessageType
import me.ywj.cloudpvp.lobby.repository.LobbyRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.redisson.api.RLock
import org.redisson.api.RedissonClient
import org.redisson.misc.CompletableFutureWrapper
import org.springframework.data.redis.core.RedisTemplate
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.Optional

class LobbyListeningServiceTest {
    @Test
    fun updateIsStoredAndBroadcast() {
        val lobbyRepository = mock(LobbyRepository::class.java)
        @Suppress("UNCHECKED_CAST")
        val redisTemplate = mock(RedisTemplate::class.java) as RedisTemplate<String, Any>
        val redissonClient = mock(RedissonClient::class.java)
        val lock = mock(RLock::class.java)
        `when`(redissonClient.getLock("LobbyLock:123")).thenReturn(lock)
        `when`(lock.tryLockAsync(anyLong(), anyLong(), org.mockito.ArgumentMatchers.eq(TimeUnit.MILLISECONDS), anyLong()))
            .thenReturn(CompletableFutureWrapper(true))
        `when`(lock.unlockAsync(anyLong()))
            .thenReturn(CompletableFutureWrapper<Void>(CompletableFuture.completedFuture(null)))
        val lobby = Lobby(123).apply { status = LobbyStatus.MATCHING }
        `when`(lobbyRepository.findById(123)).thenReturn(Optional.of(lobby))

        LobbyListeningService(lobbyRepository, redisTemplate, redissonClient).consumeLobbyStatus(
            LobbyUpdateMessage("123", LobbyStatus.WAITING, "match-1"),
        )

        assertThat(lobby.status).isEqualTo(LobbyStatus.WAITING)
        assertThat(lobby.matchId).isEqualTo("match-1")
        verify(lobbyRepository).save(lobby)
        verify(redisTemplate).convertAndSend(
            "123",
            LobbyMessage(LobbyMessageType.SHOULD_SYNC, null, ""),
        )
        verify(lock).unlockAsync(anyLong())
    }
}

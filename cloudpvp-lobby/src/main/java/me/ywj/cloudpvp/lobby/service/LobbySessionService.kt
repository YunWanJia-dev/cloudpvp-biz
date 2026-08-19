package me.ywj.cloudpvp.lobby.service

import me.ywj.cloudpvp.core.type.LobbyId
import me.ywj.cloudpvp.core.type.SteamID64
import me.ywj.cloudpvp.lobby.listener.LobbyPlayerListener
import me.ywj.cloudpvp.lobby.model.publishing.LobbyMessage
import me.ywj.cloudpvp.lobby.repository.PlayerLobbyRepository
import org.springframework.data.redis.listener.PatternTopic
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.set

@Service
class LobbySessionService(
    private val playerLobbyRepository: PlayerLobbyRepository,
    val container: RedisMessageListenerContainer,
) {
    private val listenerList = ConcurrentHashMap<SteamID64, LobbyPlayerListener>()

    fun trySubscribe(playerId: SteamID64, lobbyId: LobbyId, sendMessageFn: (LobbyMessage) -> Unit) {
        val playerLobbyPair = playerLobbyRepository.findById(playerId).orElseThrow()
        if (playerLobbyPair.lobbyId != lobbyId) {
            return
        }

        val messageListener = LobbyPlayerListener(sendMessageFn)
        container.addMessageListener(messageListener, PatternTopic(lobbyId.toString()))
        listenerList[playerId] = messageListener
    }

    fun unsubscribe(playerId: SteamID64) {
        listenerList[playerId]?.let {
            container.removeMessageListener(it);
        }
        listenerList.remove(playerId)
    }
}
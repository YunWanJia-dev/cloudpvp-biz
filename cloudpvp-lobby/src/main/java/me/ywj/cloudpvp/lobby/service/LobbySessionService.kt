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

/**
 * LobbySessionService
 * 管理比赛 WebSocket 会话与 Match Redis 频道监听器的绑定关系。
 *
 * @author sheip9
 * @since 2026/8/19 17:15
 */
@Service
class LobbySessionService(
    private val playerLobbyRepository: PlayerLobbyRepository,
    val container: RedisMessageListenerContainer,
) {
    private val listenerList = ConcurrentHashMap<SteamID64, LobbyPlayerListener>()

    /**
     * trySubscribe
     * 尝试为玩家订阅lobby的消息
     *
     * @param playerId 玩家ID
     * @param lobbyId 房间ID
     * @param sendMessageFn 发送消息的方法
     * @return 成功为true，失败false
     */
    fun trySubscribe(playerId: SteamID64, lobbyId: LobbyId, sendMessageFn: (LobbyMessage) -> Unit) : Boolean {
        val playerLobbyPair = playerLobbyRepository.findById(playerId).orElseThrow()
        if (playerLobbyPair.lobbyId != lobbyId) {
            return false
        }

        val messageListener = LobbyPlayerListener(sendMessageFn)
        container.addMessageListener(messageListener, PatternTopic(lobbyId.toString()))
        listenerList[playerId] = messageListener
        return true
    }

    /**
     * trySubscribe
     * 尝试为玩家取消订阅lobby的消息
     *
     * @param playerId 玩家ID
     */
    fun unsubscribe(playerId: SteamID64) {
        listenerList[playerId]?.let {
            container.removeMessageListener(it)
        }
        listenerList.remove(playerId)
    }
}
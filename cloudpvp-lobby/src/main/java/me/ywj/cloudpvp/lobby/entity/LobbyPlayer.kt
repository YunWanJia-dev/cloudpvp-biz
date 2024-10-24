package me.ywj.cloudpvp.lobby.entity

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.ObjectMapper
import me.ywj.cloudpvp.core.entity.BasicPlayer
import me.ywj.cloudpvp.core.type.LobbyId
import me.ywj.cloudpvp.core.type.SteamId64
import org.springframework.data.annotation.Id
import org.springframework.data.redis.connection.Message
import org.springframework.data.redis.connection.MessageListener
import org.springframework.data.redis.core.RedisHash
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession

/**
 * LobbyPlayer
 *
 * @author sheip9
 * @since 2024/10/20 16:31
 */
@RedisHash("LobbyPlayer")
class LobbyPlayer(@Id override val steamId64 : SteamId64, @JsonIgnore val session : WebSocketSession) : BasicPlayer(steamId64)  {
    var lobbyId : LobbyId? = null

    val msgListener : MessageListener = LobbyListener { it: Any -> session.sendMessage(it) }

    fun sendMessage(msg: Any){
        session.sendMessage(msg)
    }
}

class LobbyListener (val msgSender : (Any) -> Unit): MessageListener {
    override fun onMessage(message: Message, pattern: ByteArray?) {
        msgSender.invoke(message)
    }
}

private fun WebSocketSession.sendMessage(response: Any) {
    sendMessage(TextMessage(ObjectMapper().writeValueAsString(response)))
}

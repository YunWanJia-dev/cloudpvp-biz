package me.ywj.cloudpvp.lobby.entity

import com.fasterxml.jackson.annotation.JsonIgnore
import me.ywj.cloudpvp.core.entity.BasicPlayer
import me.ywj.cloudpvp.core.type.LobbyId
import me.ywj.cloudpvp.core.type.SteamID64
import me.ywj.cloudpvp.lobby.configure.RedisConfigure
import org.springframework.data.annotation.Id
import org.springframework.data.redis.connection.Message
import org.springframework.data.redis.connection.MessageListener
import org.springframework.data.redis.core.RedisHash

/**
 * LobbyPlayer
 *
 * @author sheip9
 * @since 2024/10/20 16:31
 */
@RedisHash("LobbyPlayer")
class LobbyPlayer(@Id override val steamId64 : SteamID64, @JsonIgnore val msgSender: (Any) -> Unit) : BasicPlayer(steamId64)  {
    var lobbyId : LobbyId? = null

    val msgListener : MessageListener = LobbyListener(msgSender)

    fun sendMessage(msg: Any){
        msgSender(msg)
    }
}

class LobbyListener (val msgSender : (Any) -> Unit): MessageListener {
    override fun onMessage(message: Message, pattern: ByteArray?) {
        val msg = RedisConfigure.SERIALIZER.deserialize(message.body)
        if(msg != null) {
            msgSender.invoke(msg)
        }
    }
}

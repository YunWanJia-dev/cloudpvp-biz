package me.ywj.cloudpvp.lobby.listener

import me.ywj.cloudpvp.core.utils.JacksonUtils
import me.ywj.cloudpvp.lobby.model.publishing.LobbyMessage
import org.springframework.data.redis.connection.Message
import org.springframework.data.redis.connection.MessageListener
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer


/**
 * LobbyPlayerListener
 *
 * @author sheip9
 * @since 2026/8/19 15:49
 */
class LobbyPlayerListener (
    private val msgSender: (LobbyMessage) -> Unit,
) : MessageListener {
    override fun onMessage(message: Message, pattern: ByteArray?) {

        val msg: LobbyMessage? = LOBBY_MESSAGE_SERIALIZER.deserialize(message.body)

        msg?.let {
            msgSender.invoke(msg)
        }
    }

}

private val LOBBY_MESSAGE_SERIALIZER =
    Jackson2JsonRedisSerializer(LobbyMessage::class.java).also { it.setObjectMapper(JacksonUtils.INSTANCE) }
package me.ywj.cloudpvp.lobby.entity

import com.fasterxml.jackson.annotation.JsonIgnore
import me.ywj.cloudpvp.core.entity.BasicPlayer
import me.ywj.cloudpvp.core.model.lobby.LobbyMessage
import me.ywj.cloudpvp.core.model.lobby.LobbyMessageType
import me.ywj.cloudpvp.core.type.LobbyId
import me.ywj.cloudpvp.core.type.SteamID64
import me.ywj.cloudpvp.lobby.configurations.RedisConfiguration
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
class LobbyPlayer(
    @Id override val steamID64: SteamID64,
    @JsonIgnore val msgSender: (Any) -> Unit,
    @JsonIgnore private val closeHandler: (LobbyPlayer) -> Unit = { _ -> },
) :
    BasicPlayer(steamID64) {
    constructor(steamID64: SteamID64, msgSender: (Any) -> Unit) : this(steamID64, msgSender, { _ -> })

    var lobbyId: LobbyId? = null

    @JsonIgnore
    val msgListener: MessageListener = LobbyListener(steamID64, msgSender) { closeConnection() }

    fun sendMessage(msg: Any) {
        msgSender(msg)
    }

    /**
     * 关闭当前玩家绑定的 WebSocket 连接。
     */
    fun closeConnection() {
        closeHandler(this)
    }
}

class LobbyListener(
    private val playerId: SteamID64,
    private val msgSender: (Any) -> Unit,
    private val closeHandler: () -> Unit,
) : MessageListener {
    override fun onMessage(message: Message, pattern: ByteArray?) {
        val msg = RedisConfiguration.SERIALIZER.deserialize(message.body)
        if (msg != null) {
            // 自己离开或大厅销毁时，本连接必须先取消订阅再关闭，避免继续接收旧大厅消息。
            if (shouldCloseConnection(msg)) {
                closeHandler()
                return
            }
            msgSender.invoke(msg)
        }
    }

    private fun shouldCloseConnection(msg: Any): Boolean {
        val type = lobbyMessageType(msg) ?: return false
        if (type == LobbyMessageType.LOBBY_DESTROYED) {
            return true
        }
        return type == LobbyMessageType.LEAVE && (lobbyMessageData(msg) as? Number)?.toLong() == playerId
    }

    private fun lobbyMessageType(msg: Any): LobbyMessageType? {
        return when (msg) {
            is LobbyMessage -> msg.type
            is Map<*, *> -> {
                when (val type = msg["type"]) {
                    is LobbyMessageType -> type
                    is String -> runCatching { LobbyMessageType.valueOf(type) }.getOrNull()
                    else -> null
                }
            }
            else -> null
        }
    }

    private fun lobbyMessageData(msg: Any): Any? {
        return when (msg) {
            is LobbyMessage -> msg.data
            is Map<*, *> -> msg["data"]
            else -> msg
        }
    }
}

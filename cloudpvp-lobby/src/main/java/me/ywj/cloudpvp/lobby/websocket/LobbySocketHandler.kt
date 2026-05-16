package me.ywj.cloudpvp.lobby.websocket

import me.ywj.cloudpvp.core.constant.header.Attributes
import me.ywj.cloudpvp.core.model.base.ErrorResponse
import me.ywj.cloudpvp.core.model.base.ErrorType
import me.ywj.cloudpvp.core.type.SteamID64
import me.ywj.cloudpvp.core.utils.JacksonUtils
import me.ywj.cloudpvp.core.utils.LobbyUtils
import me.ywj.cloudpvp.core.utils.PlayerUtils
import me.ywj.cloudpvp.lobby.entity.LobbyPlayer
import me.ywj.cloudpvp.lobby.exceptions.LobbyNotExist
import me.ywj.cloudpvp.lobby.service.LobbyService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Controller
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.AbstractWebSocketHandler
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator
import org.springframework.web.util.UriTemplate

/**
 * StateSocketHandler
 *
 * @author sheip9
 * @since 2024/10/20 15:44
 */
@Controller
class LobbySocketHandler @Autowired constructor(private val lobbyService: LobbyService) : AbstractWebSocketHandler(),
    WebSocketHandler {
    companion object {
        const val PARAM_LOBBY_ID = "lobbyId"
        const val PATH = "/ws/{${PARAM_LOBBY_ID}}"
        /**
         * 单次发送持有锁的最长时间，避免慢连接让同一会话的后续消息长期排队。
         */
        private const val SEND_TIME_LIMIT_MILLIS = 10_000
        /**
         * 装饰器排队发送消息的最大缓冲，防止异常连接持续堆积待发送数据。
         */
        private const val SEND_BUFFER_SIZE_LIMIT_BYTES = 64 * 1024
        private val URI_TEMPLATE = UriTemplate(PATH)
        private val PLAYER_MAP = HashMap<SteamID64, LobbyPlayer>()
    }

    private fun WebSocketSession.getPlayerId(): SteamID64? {
        return (attributes[Attributes.ID] as SteamID64?)
    }

    private fun WebSocketSession.getRequestLobbyId(): Int? {
        return URI_TEMPLATE.match(uri!!.path)[PARAM_LOBBY_ID]?.toIntOrNull()
    }

    private fun WebSocketSession.checkSessionIsValid(): Boolean {
        val playerIdIsValid = PlayerUtils.checkIdIsValid(getPlayerId())
        val lobbyIdIsValid = LobbyUtils.checkLobbyIdIsValid(getRequestLobbyId())
        return playerIdIsValid && lobbyIdIsValid
    }

    private fun WebSocketSession.sendMessage(response: Any) {
        if (!isOpen) {
            return
        }
        if (response is String) {
            sendMessage(TextMessage(response))
            return
        }
        sendMessage(TextMessage(JacksonUtils.serialize(response)))
    }

    override fun afterConnectionEstablished(session: WebSocketSession) {
        // Redis 监听回调和连接建立流程都可能向同一个连接发送消息；原始 session 不保证并发发送安全，
        // 使用装饰器串行化发送，并通过超时和缓冲限制避免慢连接长期阻塞。
        val safeSession = ConcurrentWebSocketSessionDecorator(
            session,
            SEND_TIME_LIMIT_MILLIS,
            SEND_BUFFER_SIZE_LIMIT_BYTES,
        )
        if (!safeSession.checkSessionIsValid()) {
            safeSession.sendMessage(ErrorResponse(ErrorType.PARAM_INVALID, ""))
            safeSession.close()
            return
        }

        val playerId = safeSession.getPlayerId()!!
        val player = LobbyPlayer(playerId) { it: Any -> safeSession.sendMessage(it) }

        try {
            lobbyService.joinLobby(player, safeSession.getRequestLobbyId()!!)
            PLAYER_MAP[playerId] = player
        } catch (_: LobbyNotExist) {
            safeSession.sendMessage(ErrorResponse(ErrorType.LOBBY_NOT_EXIST, ""))
            safeSession.close()
        }
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        val playerId = session.getPlayerId()
        val player = PLAYER_MAP[playerId] ?: return
        lobbyService.leaveLobby(player)
        PLAYER_MAP.remove(playerId)
    }

    override fun handleTextMessage(
        session: WebSocketSession,
        message: TextMessage,
    ) {
        val player = PLAYER_MAP[session.getPlayerId()!!]
        lobbyService.playerTexting(player!!, message.payload)
    }
}


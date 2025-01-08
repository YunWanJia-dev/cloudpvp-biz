package me.ywj.cloudpvp.lobby.websocket

import me.ywj.cloudpvp.core.constant.header.Attributes
import me.ywj.cloudpvp.core.model.base.ErrorResponse
import me.ywj.cloudpvp.core.model.base.ErrorType
import me.ywj.cloudpvp.core.type.SteamID64
import me.ywj.cloudpvp.core.type.toSteamID64
import me.ywj.cloudpvp.core.utils.JacksonUtils
import me.ywj.cloudpvp.core.utils.LobbyUtils
import me.ywj.cloudpvp.core.utils.PlayerUtils
import me.ywj.cloudpvp.lobby.entity.LobbyPlayer
import me.ywj.cloudpvp.lobby.exception.LobbyNotExist
import me.ywj.cloudpvp.lobby.service.LobbyService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Controller
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.AbstractWebSocketHandler
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
        if (!session.checkSessionIsValid()) {
            session.sendMessage(ErrorResponse(ErrorType.PARAM_INVALID, ""))
            session.close()
            return
        }

        val playerId = session.getPlayerId()!!
        val player = LobbyPlayer(playerId) { it: Any -> session.sendMessage(it) }

        try {
            lobbyService.joinLobby(player, session.getRequestLobbyId()!!)
            PLAYER_MAP[playerId] = player
        } catch (_: LobbyNotExist) {
            session.sendMessage(ErrorResponse(ErrorType.LOBBY_NOT_EXIST, ""))
            session.close()
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
        message: TextMessage
    ) {
        val player = PLAYER_MAP[session.getPlayerId()!!]
        lobbyService.playerTexting(player!!, message.payload)
    }
}


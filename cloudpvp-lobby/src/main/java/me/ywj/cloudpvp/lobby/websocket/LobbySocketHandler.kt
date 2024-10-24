package me.ywj.cloudpvp.lobby.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import me.ywj.cloudpvp.core.model.base.ErrorResponse
import me.ywj.cloudpvp.core.model.base.ErrorType
import me.ywj.cloudpvp.core.type.SteamId64
import me.ywj.cloudpvp.lobby.entity.LobbyPlayer
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
class LobbySocketHandler @Autowired constructor(val lobbyService: LobbyService) : AbstractWebSocketHandler(), WebSocketHandler {
    companion object {
        const val PARAM_LOBBY_ID = "lobbyId"
        const val PATH = "/ws/{${PARAM_LOBBY_ID}}"
        val URI_TEMPLATE = UriTemplate(PATH)
        private val PLAYER_MAP = HashMap<SteamId64, LobbyPlayer>()
    }
    override fun afterConnectionEstablished(session: WebSocketSession) {
        val steamId64 = session.attributes["steamId"] as SteamId64?
        val player = LobbyPlayer(steamId64 ?: 1L, session)
        if(player.lobbyId <= 0){
            session.sendMessage(ErrorResponse(ErrorType.LOBBY_ID_INVALID, ""))
            session.close()
            return
        }
        runCatching {
            lobbyService.joinLobby(player)
        }.onFailure {
            session.sendMessage(ErrorResponse(ErrorType.LOBBY_NOT_EXIST, ""))
            session.close()
        }
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        val steamId64 = session.attributes["steamId"] as SteamId64?
        val player = PLAYER_MAP[steamId64]
        lobbyService.leaveLobby(player!!)
        PLAYER_MAP.remove(steamId64)
    }
    
}

private fun WebSocketSession.sendMessage(response: Any) {
    sendMessage(TextMessage(ObjectMapper().writeValueAsString(response)))
}

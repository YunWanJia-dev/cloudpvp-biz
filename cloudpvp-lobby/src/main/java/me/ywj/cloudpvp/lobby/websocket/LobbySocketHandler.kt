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
import kotlin.reflect.typeOf

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
        private val PLAYER_MAP = HashMap<SteamId64, Any>()
        private val URI_TEMPLATE = UriTemplate(PATH)
    }
    override fun afterConnectionEstablished(session: WebSocketSession, ) {
        val lobbyId = URI_TEMPLATE.match(session.uri!!.path)[PARAM_LOBBY_ID] as String
        val steamId64 = session.attributes["steamId"] as SteamId64?
        val f = { session.sendMessage("") }
        val player = LobbyPlayer(steamId64 ?: 1L)
//            .apply { 
//            xx =f
//        }
        
        runCatching {
            lobbyService.joinLobby(player, lobbyId.toInt())
        }.onFailure {
            session.sendMessage(ErrorResponse(ErrorType.LOBBY_NOT_EXIST, ""))
            session.close()
        }
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        val lobbyId = URI_TEMPLATE.match(session.uri!!.path)[PARAM_LOBBY_ID] as String
        val steamId64 = session.attributes["steamId"] as SteamId64?
        val player = LobbyPlayer(steamId64 ?: 1L)
        lobbyService.leaveLobby(player, lobbyId.toInt())
    }
}

private fun WebSocketSession.sendMessage(response: Any) {
    sendMessage(TextMessage(ObjectMapper().writeValueAsString(response)))
}

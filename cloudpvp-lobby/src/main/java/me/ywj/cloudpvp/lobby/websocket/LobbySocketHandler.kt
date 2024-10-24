package me.ywj.cloudpvp.lobby.websocket

import me.ywj.cloudpvp.core.model.base.ErrorResponse
import me.ywj.cloudpvp.core.model.base.ErrorType
import me.ywj.cloudpvp.core.type.SteamId64
import me.ywj.cloudpvp.core.utils.LobbyUtils
import me.ywj.cloudpvp.core.utils.PlayerUtils
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
        private val URI_TEMPLATE = UriTemplate(PATH)
        private val PLAYER_MAP = HashMap<SteamId64, LobbyPlayer>()
    }
    
    private fun WebSocketSession.getPlayerId(): SteamId64? {
        return attributes["steamId"] as SteamId64?
    }
    
    private fun WebSocketSession.getRequestLobbyId(): Int {
        return(URI_TEMPLATE.match(uri!!.path)[PARAM_LOBBY_ID] as String).toInt()
    }

    private fun WebSocketSession.checkSessionIsValid(): Boolean {
        val playerIdIsValid = PlayerUtils.checkIdIsValid(getPlayerId())
        val lobbyIdIsValid = LobbyUtils.checkLobbyIdIsValid(getRequestLobbyId())
        return playerIdIsValid && lobbyIdIsValid
    }
    
    override fun afterConnectionEstablished(session: WebSocketSession) {
        val playerId = session.getPlayerId()!!
        val player = LobbyPlayer(playerId, session)
        
        if(!session.checkSessionIsValid()) {
            player.sendMessage(ErrorResponse(ErrorType.PARAM_INVALID, ""))
            session.close()
        }
        
        runCatching {
            lobbyService.joinLobby(player, session.getRequestLobbyId())
            PLAYER_MAP[playerId] = player
        }.onFailure {
            player.sendMessage(ErrorResponse(ErrorType.LOBBY_NOT_EXIST, ""))
            session.close()
        }
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        val playerId = session.getPlayerId()!!
        val player = PLAYER_MAP[playerId]
        lobbyService.leaveLobby(player!!)
        PLAYER_MAP.remove(playerId)
    }

    override fun handleTextMessage(
        session: WebSocketSession,
        message: TextMessage
    ) {
        message.payload
    }
}


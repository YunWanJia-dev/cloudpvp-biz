package me.ywj.cloudpvp.state.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import me.ywj.cloudpvp.core.constant.steam.SteamUser
import me.ywj.cloudpvp.core.model.ErrorResponse
import me.ywj.cloudpvp.core.model.ErrorType
import me.ywj.cloudpvp.state.constant.StateEnum
import me.ywj.cloudpvp.state.service.PlayerStateService
import me.ywj.cloudpvp.core.type.SteamId
import me.ywj.cloudpvp.core.utils.PlayerUtils
import me.ywj.cloudpvp.state.entity.PlayerState
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Controller
import org.springframework.web.socket.AbstractWebSocketMessage
import org.springframework.web.socket.BinaryMessage
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.WebSocketMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.AbstractWebSocketHandler

/**
 * StateSocketHandler
 *
 * @author sheip9
 * @since 2024/10/17 12:21
 */
@Controller
class StateSocketHandler @Autowired constructor(private val playerStateService: PlayerStateService) : AbstractWebSocketHandler(), WebSocketHandler {
    companion object {
        private val PLAYER_MAP = HashMap<SteamId, PlayerState>()
    }
    override fun afterConnectionEstablished(session: WebSocketSession) {
        val steamId = session.attributes["steamId"] as SteamId?
        if (PlayerUtils.checkIdIsValid(steamId)) {
            session.sendMessage(ErrorResponse(ErrorType.PLAYER_ID_INVALID, ""))
            session.close()
        }
        val player = PlayerState(steamId!!).apply {
            state = StateEnum.ONLINE
        }
        PLAYER_MAP[steamId] = player
        playerStateService.setState(player)
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        val steamId = session.attributes["steamId"] as SteamId
        playerStateService.onDisconnect(PLAYER_MAP[steamId]!!)
        PLAYER_MAP.remove(steamId)
    }
}

private fun WebSocketSession.sendMessage(response: Any) {
    sendMessage(TextMessage(ObjectMapper().writeValueAsString(response)))
}

package me.ywj.cloudpvp.lobby.websocket

import me.ywj.cloudpvp.core.constant.header.Attributes
import me.ywj.cloudpvp.core.model.base.ErrorResponse
import me.ywj.cloudpvp.core.model.base.ErrorType
import me.ywj.cloudpvp.core.type.SteamID64
import me.ywj.cloudpvp.core.utils.JacksonUtils
import me.ywj.cloudpvp.core.utils.PlayerUtils
import me.ywj.cloudpvp.lobby.entity.Match
import me.ywj.cloudpvp.lobby.service.MatchSessionService
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
 * MatchSocketHandler
 * 比赛状态 WebSocket 处理器。
 *
 * @author sheip9
 * @since 2026/8/20 16:57
 */
@Controller
class MatchSocketHandler @Autowired constructor(
    private val matchSessionService: MatchSessionService,
) : AbstractWebSocketHandler(), WebSocketHandler {
    companion object {
        const val PARAM_MATCH_ID = "matchId"
        const val PATH = "/ws/match/{${PARAM_MATCH_ID}}"
        private val URI_TEMPLATE = UriTemplate(PATH)
        private const val SEND_TIME_LIMIT_MILLIS = 10_000
        private const val SEND_BUFFER_SIZE_LIMIT_BYTES = 64 * 1024
    }

    private fun WebSocketSession.getPlayerId(): SteamID64? {
        return attributes[Attributes.ID] as SteamID64?
    }

    private fun WebSocketSession.getRequestMatchId(): String? {
        val requestPath = uri?.path ?: return null
        return URI_TEMPLATE.match(requestPath)[PARAM_MATCH_ID]?.takeIf { it.isNotBlank() }
    }

    private fun WebSocketSession.checkSessionIsValid(): Boolean {
        return PlayerUtils.checkIdIsValid(getPlayerId()) && getRequestMatchId() != null
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

    /**
     * 建立 WebSocket 连接后监听目标比赛。
     *
     * @param session 新建立的 WebSocket 会话
     */
    override fun afterConnectionEstablished(session: WebSocketSession) {
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
        val matchId = safeSession.getRequestMatchId()!!
        val sendMatchFn = fun(match: Match) {
            safeSession.sendMessage(match)
        }

        if (!matchSessionService.trySubscribe(playerId, matchId, sendMatchFn)) {
            safeSession.close()
        }
    }

    /**
     * WebSocket 连接关闭后取消比赛监听。
     *
     * @param session 已关闭的 WebSocket 会话
     * @param status 连接关闭状态
     */
    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        session.getPlayerId()?.let {
            matchSessionService.unsubscribe(it)
        }
    }
}

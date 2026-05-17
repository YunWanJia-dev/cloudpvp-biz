package me.ywj.cloudpvp.lobby.websocket

import kotlinx.coroutines.runBlocking
import me.ywj.cloudpvp.core.constant.header.Attributes
import me.ywj.cloudpvp.core.model.base.ErrorResponse
import me.ywj.cloudpvp.core.model.base.ErrorType
import me.ywj.cloudpvp.core.type.SteamID64
import me.ywj.cloudpvp.core.utils.JacksonUtils
import me.ywj.cloudpvp.core.utils.LobbyUtils
import me.ywj.cloudpvp.core.utils.PlayerUtils
import me.ywj.cloudpvp.lobby.entity.LobbyPlayer
import me.ywj.cloudpvp.lobby.exceptions.LobbyBusyException
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
        private const val ATTR_LOBBY_PLAYER = "cloudpvp.lobby.player"
    }

    /**
     * 从握手属性中读取当前玩家 ID。
     *
     * @return 当前连接绑定的 Steam ID64
     */
    private fun WebSocketSession.getPlayerId(): SteamID64? {
        return (attributes[Attributes.ID] as SteamID64?)
    }

    /**
     * 从 WebSocket 请求路径中解析目标大厅 ID。
     *
     * @return 请求路径中的大厅 ID
     */
    private fun WebSocketSession.getRequestLobbyId(): Int? {
        return URI_TEMPLATE.match(uri!!.path)[PARAM_LOBBY_ID]?.toIntOrNull()
    }

    /**
     * 校验连接携带的玩家 ID 和大厅 ID 是否可用于加入大厅。
     *
     * @return true 表示连接参数有效
     */
    private fun WebSocketSession.checkSessionIsValid(): Boolean {
        val playerIdIsValid = PlayerUtils.checkIdIsValid(getPlayerId())
        val lobbyIdIsValid = LobbyUtils.checkLobbyIdIsValid(getRequestLobbyId())
        return playerIdIsValid && lobbyIdIsValid
    }

    /**
     * 向当前 WebSocket 连接发送文本或对象消息。
     *
     * @param response 需要发送给客户端的响应对象
     */
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
     * 建立 WebSocket 连接后监听目标大厅。
     *
     * @param session 新建立的 WebSocket 会话
     */
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
        val targetLobbyId = safeSession.getRequestLobbyId()!!
        val player = LobbyPlayer(playerId) { it: Any -> safeSession.sendMessage(it) }

        runBlocking {
            try {
                val subscribed = lobbyService.subscribeLobby(player, targetLobbyId)
                if (!subscribed) {
                    safeSession.sendMessage(ErrorResponse(ErrorType.PARAM_INVALID, ""))
                    safeSession.close()
                    return@runBlocking
                }
                safeSession.attributes[ATTR_LOBBY_PLAYER] = player
                if (!safeSession.isOpen) {
                    safeSession.attributes.remove(ATTR_LOBBY_PLAYER)
                    lobbyService.unsubscribeLobby(player)
                    return@runBlocking
                }
            } catch (_: LobbyNotExist) {
                safeSession.sendMessage(ErrorResponse(ErrorType.LOBBY_NOT_EXIST, ""))
                safeSession.close()
            } catch (_: LobbyBusyException) {
                safeSession.sendMessage(ErrorResponse(ErrorType.LOBBY_BUSY, ""))
                safeSession.close()
            }
        }
    }

    /**
     * WebSocket 连接关闭后取消大厅监听。
     *
     * @param session 已关闭的 WebSocket 会话
     * @param status 连接关闭状态
     */
    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        val player = session.attributes.remove(ATTR_LOBBY_PLAYER) as? LobbyPlayer ?: return
        lobbyService.unsubscribeLobby(player)
    }
}


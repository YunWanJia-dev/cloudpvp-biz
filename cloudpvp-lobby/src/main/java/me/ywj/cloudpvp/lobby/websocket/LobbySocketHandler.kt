package me.ywj.cloudpvp.lobby.websocket

import jakarta.annotation.PreDestroy
import kotlinx.coroutines.*
import me.ywj.cloudpvp.core.constant.header.Attributes
import me.ywj.cloudpvp.core.model.base.ErrorResponse
import me.ywj.cloudpvp.core.model.base.ErrorType
import me.ywj.cloudpvp.core.type.SteamID64
import me.ywj.cloudpvp.core.utils.JacksonUtils
import me.ywj.cloudpvp.core.utils.LobbyUtils
import me.ywj.cloudpvp.core.utils.PlayerUtils
import me.ywj.cloudpvp.lobby.entity.LobbyPlayer
import me.ywj.cloudpvp.lobby.exceptions.LobbySocketError
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
 * LobbySocketHandler
 * 大厅 WebSocket 处理器。
 *
 * @author sheip9
 * @since 2024/10/20 15:44
 */
@Controller
class LobbySocketHandler : AbstractWebSocketHandler,
    WebSocketHandler {
    private val lobbyService: LobbyService

    /**
     * 大厅订阅任务使用处理器自有协程作用域，避免在 WebSocket 容器线程中等待 Redis 锁和仓储 I/O。
     */
    private val subscriptionScope: CoroutineScope

    @Autowired
    constructor(lobbyService: LobbyService) : this(
        lobbyService,
        CoroutineScope(SupervisorJob() + Dispatchers.IO),
    )

    internal constructor(lobbyService: LobbyService, subscriptionScope: CoroutineScope) : super() {
        this.lobbyService = lobbyService
        this.subscriptionScope = subscriptionScope
    }

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
     * 清理当前连接的大厅订阅并关闭 WebSocket。
     *
     * @param session 当前 WebSocket 会话
     * @param player 当前连接绑定的大厅玩家状态
     */
    private fun closeSubscribedSession(session: WebSocketSession, player: LobbyPlayer) {
        cleanupSubscribedPlayer(session, player)
        if (!session.isOpen) {
            return
        }
        try {
            session.close()
        } catch (_: Exception) {
            // 关闭失败不能影响订阅清理；连接关闭流程会在容器侧继续收敛。
        }
    }

    /**
     * 清理已建立或半建立的大厅订阅状态。
     *
     * @param session 当前 WebSocket 会话
     * @param fallbackPlayer 订阅过程已记录大厅 ID 但尚未写入 session 属性时使用的兜底玩家
     */
    private fun cleanupSubscribedPlayer(session: WebSocketSession, fallbackPlayer: LobbyPlayer? = null) {
        val storedPlayer = session.attributes.remove(ATTR_LOBBY_PLAYER) as? LobbyPlayer
        val playerToCleanup = storedPlayer ?: fallbackPlayer?.takeIf { it.lobbyId != null } ?: return
        lobbyService.unsubscribeLobby(playerToCleanup)
    }

    /**
     * 向客户端返回订阅阶段错误并关闭连接。
     *
     * @param session 当前 WebSocket 会话
     * @param error 订阅阶段捕获到的异常
     */
    private fun closeWithSubscriptionError(session: WebSocketSession, error: Throwable) {
        // 订阅失败可能是大厅运行时状态，保留具体错误类型供客户端选择正确提示和重试策略。
        val errorType = (error as? LobbySocketError)?.errorType ?: ErrorType.PARAM_INVALID
        runCatching { session.sendMessage(ErrorResponse(errorType, error.message ?: "")) }
        runCatching { session.close() }
    }

    /**
     * 销毁处理器时取消尚未完成的订阅任务。
     */
    @PreDestroy
    fun destroy() {
        subscriptionScope.cancel()
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
        val player = LobbyPlayer(
            playerId,
            { it: Any -> safeSession.sendMessage(it) },
            { closeSubscribedSession(safeSession, it) },
        )

        subscriptionScope.launch {
            try {
                val subscribed = lobbyService.subscribeLobby(player, targetLobbyId)
                if (!subscribed) {
                    safeSession.sendMessage(ErrorResponse(ErrorType.PARAM_INVALID, ""))
                    safeSession.close()
                    return@launch
                }
                safeSession.attributes[ATTR_LOBBY_PLAYER] = player
                if (!safeSession.isOpen) {
                    // 订阅完成前连接可能已断开；这里补偿清理，避免 Redis 监听器泄漏。
                    cleanupSubscribedPlayer(safeSession, player)
                    return@launch
                }
            } catch (e: Throwable) {
                if (e is CancellationException) {
                    throw e
                }
                cleanupSubscribedPlayer(safeSession, player)
                closeWithSubscriptionError(safeSession, e)
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
        // 允许玩家短暂掉线后重连到大厅，因此这里只取消订阅，不调用 leaveLobby。
        // 潜在的无效大厅由定时任务或其他清理流程处理。
        cleanupSubscribedPlayer(session)
    }
}


package me.ywj.cloudpvp.lobby.websocket

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import me.ywj.cloudpvp.core.constant.header.Attributes
import me.ywj.cloudpvp.core.model.base.ErrorType
import me.ywj.cloudpvp.core.model.lobby.LobbyMessage
import me.ywj.cloudpvp.core.model.lobby.LobbyMessageType
import me.ywj.cloudpvp.core.type.SteamID64
import me.ywj.cloudpvp.lobby.entity.Lobby
import me.ywj.cloudpvp.lobby.entity.LobbyPlayer
import me.ywj.cloudpvp.lobby.exceptions.LobbyBusyException
import me.ywj.cloudpvp.lobby.exceptions.LobbyNotExist
import me.ywj.cloudpvp.lobby.service.LobbyLifecycleService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * LobbySocketHandlerTest
 * 大厅 WebSocket 处理器单元测试。
 *
 * @author sheip9
 * @since 2026/5/16 18:00
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LobbySocketHandlerTest {
    companion object {
        private const val VALID_PLAYER_ID = 76561197960265729L
    }

    /**
     * 验证连接建立后订阅目标大厅，并把订阅玩家保存在当前 session 中供断开时清理。
     */
    @Test
    fun afterConnectionEstablishedSubscribesAndStoresPlayerForDisconnect() = runTest {
        val lobbyService = Mockito.mock(LobbyLifecycleService::class.java)
        val handler = lobbySocketHandler(lobbyService)
        val subscribedPlayer = AtomicReference<LobbyPlayer>()
        val lobby = Lobby(123, arrayListOf(VALID_PLAYER_ID))

        Mockito.doAnswer { invocation ->
            val player = invocation.getArgument<LobbyPlayer>(0)
            player.lobbyId = 123
            subscribedPlayer.set(player)
            player.sendMessage(LobbyMessage(LobbyMessageType.LOBBY_SNAPSHOT).apply {
                data = lobby
            })
            true
        }.`when`(lobbyService).subscribeLobby(anyLobbyPlayer(), eq(123))

        val session = lobbySession(VALID_PLAYER_ID, "/ws/123")

        handler.afterConnectionEstablished(session)
        advanceUntilIdle()

        val player = subscribedPlayer.get()
        assertThat(player).isNotNull()
        assertThat(player.steamID64).isEqualTo(VALID_PLAYER_ID)
        assertThat(session.attributes.values).contains(player)
        verify(lobbyService).subscribeLobby(player, 123)
        assertSentSnapshot(session)

        handler.afterConnectionClosed(session, CloseStatus.NORMAL)

        verify(lobbyService).unsubscribeLobby(player)
        assertThat(session.attributes.values).doesNotContain(player)
    }

    /**
     * 验证频道消息触发的连接关闭回调会先清理订阅状态，再关闭底层 WebSocket。
     */
    @Test
    fun playerCloseConnectionUnsubscribesAndClosesSession() = runTest {
        val lobbyService = Mockito.mock(LobbyLifecycleService::class.java)
        val handler = lobbySocketHandler(lobbyService)
        val subscribedPlayer = AtomicReference<LobbyPlayer>()

        Mockito.doAnswer { invocation ->
            val player = invocation.getArgument<LobbyPlayer>(0)
            player.lobbyId = 123
            subscribedPlayer.set(player)
            true
        }.`when`(lobbyService).subscribeLobby(anyLobbyPlayer(), eq(123))

        val session = lobbySession(VALID_PLAYER_ID, "/ws/123")

        handler.afterConnectionEstablished(session)
        advanceUntilIdle()

        val player = subscribedPlayer.get()
        assertThat(player).isNotNull()

        player.closeConnection()

        verify(lobbyService).unsubscribeLobby(player)
        verify(session).close()
        assertThat(session.attributes.values).doesNotContain(player)
    }

    /**
     * 验证订阅失败时关闭连接，且不保存需要断开清理的玩家状态。
     */
    @Test
    fun afterConnectionEstablishedClosesWhenSubscribeReturnsFalse() = runTest {
        val lobbyService = Mockito.mock(LobbyLifecycleService::class.java)
        val handler = lobbySocketHandler(lobbyService)

        Mockito.doReturn(false).`when`(lobbyService).subscribeLobby(anyLobbyPlayer(), eq(123))

        val session = lobbySession(VALID_PLAYER_ID, "/ws/123")

        handler.afterConnectionEstablished(session)
        advanceUntilIdle()

        assertSentError(session, ErrorType.PARAM_INVALID)
        verify(session).close()
        verify(lobbyService, never()).unsubscribeLobby(anyLobbyPlayer())
        assertThat(session.attributes.values).noneMatch { it is LobbyPlayer }
    }

    /**
     * 验证订阅不存在的大厅时保留大厅不存在错误类型，避免客户端误判为参数错误。
     */
    @Test
    fun afterConnectionEstablishedPreservesLobbyNotExistErrorType() = runTest {
        val lobbyService = Mockito.mock(LobbyLifecycleService::class.java)
        val handler = lobbySocketHandler(lobbyService)

        Mockito.doAnswer { throw LobbyNotExist() }.`when`(lobbyService).subscribeLobby(anyLobbyPlayer(), eq(123))

        val session = lobbySession(VALID_PLAYER_ID, "/ws/123")

        handler.afterConnectionEstablished(session)
        advanceUntilIdle()

        assertSentError(session, ErrorType.LOBBY_NOT_EXIST)
        verify(session).close()
        verify(lobbyService, never()).unsubscribeLobby(anyLobbyPlayer())
        assertThat(session.attributes.values).noneMatch { it is LobbyPlayer }
    }

    /**
     * 验证订阅遇到大厅并发占用时保留繁忙错误类型，供客户端按运行时状态处理。
     */
    @Test
    fun afterConnectionEstablishedPreservesLobbyBusyErrorType() = runTest {
        val lobbyService = Mockito.mock(LobbyLifecycleService::class.java)
        val handler = lobbySocketHandler(lobbyService)

        Mockito.doAnswer { throw LobbyBusyException(123) }.`when`(lobbyService).subscribeLobby(anyLobbyPlayer(), eq(123))

        val session = lobbySession(VALID_PLAYER_ID, "/ws/123")

        handler.afterConnectionEstablished(session)
        advanceUntilIdle()

        assertSentError(session, ErrorType.LOBBY_BUSY)
        verify(session).close()
        verify(lobbyService, never()).unsubscribeLobby(anyLobbyPlayer())
        assertThat(session.attributes.values).noneMatch { it is LobbyPlayer }
    }

    /**
     * 验证订阅任务完成前连接已关闭时，处理器会补偿取消 Redis 监听器。
     */
    @Test
    fun afterConnectionEstablishedUnsubscribesWhenSessionClosedBeforeSubscribeCompletes() = runTest {
        val lobbyService = Mockito.mock(LobbyLifecycleService::class.java)
        val handler = lobbySocketHandler(lobbyService)
        val sessionOpen = AtomicBoolean(true)
        val subscribedPlayer = AtomicReference<LobbyPlayer>()

        Mockito.doAnswer { invocation ->
            val player = invocation.getArgument<LobbyPlayer>(0)
            player.lobbyId = 123
            subscribedPlayer.set(player)
            true
        }.`when`(lobbyService).subscribeLobby(anyLobbyPlayer(), eq(123))

        val session = lobbySession(VALID_PLAYER_ID, "/ws/123") { sessionOpen.get() }

        handler.afterConnectionEstablished(session)
        sessionOpen.set(false)
        handler.afterConnectionClosed(session, CloseStatus.NORMAL)
        advanceUntilIdle()

        val player = subscribedPlayer.get()
        assertThat(player).isNotNull()
        verify(lobbyService).unsubscribeLobby(player)
        assertThat(session.attributes.values).doesNotContain(player)
    }

    /**
     * 验证快照发送失败时由处理器统一清理已经建立的订阅。
     */
    @Test
    fun afterConnectionEstablishedUnsubscribesWhenSnapshotSendFails() = runTest {
        val lobbyService = Mockito.mock(LobbyLifecycleService::class.java)
        val handler = lobbySocketHandler(lobbyService)
        val subscribedPlayer = AtomicReference<LobbyPlayer>()
        val lobby = Lobby(123, arrayListOf(VALID_PLAYER_ID))

        Mockito.doAnswer { invocation ->
            val player = invocation.getArgument<LobbyPlayer>(0)
            player.lobbyId = 123
            subscribedPlayer.set(player)
            player.sendMessage(LobbyMessage(LobbyMessageType.LOBBY_SNAPSHOT).apply {
                data = lobby
            })
            true
        }.`when`(lobbyService).subscribeLobby(anyLobbyPlayer(), eq(123))

        val session = lobbySession(VALID_PLAYER_ID, "/ws/123")
        Mockito.doThrow(IllegalStateException("closed"))
            .`when`(session).sendMessage(any(TextMessage::class.java))

        handler.afterConnectionEstablished(session)
        advanceUntilIdle()

        val player = subscribedPlayer.get()
        assertThat(player).isNotNull()
        verify(lobbyService).unsubscribeLobby(player)
        verify(session).close()
        assertThat(session.attributes.values).doesNotContain(player)
    }

    /**
     * 验证订阅过程已记录大厅 ID 后失败时，处理器仍会取消这个半注册状态。
     */
    @Test
    fun afterConnectionEstablishedUnsubscribesWhenSubscribeFailsAfterLobbyIdRecorded() = runTest {
        val lobbyService = Mockito.mock(LobbyLifecycleService::class.java)
        val handler = lobbySocketHandler(lobbyService)
        val subscribedPlayer = AtomicReference<LobbyPlayer>()

        Mockito.doAnswer { invocation ->
            val player = invocation.getArgument<LobbyPlayer>(0)
            player.lobbyId = 123
            subscribedPlayer.set(player)
            throw IllegalStateException("listener failed")
        }.`when`(lobbyService).subscribeLobby(anyLobbyPlayer(), eq(123))

        val session = lobbySession(VALID_PLAYER_ID, "/ws/123")

        handler.afterConnectionEstablished(session)
        advanceUntilIdle()

        val player = subscribedPlayer.get()
        assertThat(player).isNotNull()
        verify(lobbyService).unsubscribeLobby(player)
        verify(session).close()
        assertThat(session.attributes.values).doesNotContain(player)
    }

    /**
     * 验证无效连接参数会在进入服务层前被拒绝。
     */
    @Test
    fun invalidSessionClosesWithoutSubscribing() {
        val lobbyService = Mockito.mock(LobbyLifecycleService::class.java)
        val handler = LobbySocketHandler(lobbyService)
        val session = lobbySession(VALID_PLAYER_ID, "/ws/not-a-lobby")

        handler.afterConnectionEstablished(session)

        assertSentError(session, ErrorType.PARAM_INVALID)
        verify(session).close()
        verifyNoInteractions(lobbyService)
    }

    /**
     * 构造带有玩家 ID 和目标大厅路径的 WebSocket 会话。
     *
     * @param playerId 当前玩家 ID
     * @param path WebSocket 请求路径
     * @return 可通过处理器校验的 WebSocket 会话
     */
    private fun lobbySession(
        playerId: SteamID64,
        path: String,
        isOpen: () -> Boolean = { true },
    ): WebSocketSession {
        val session = Mockito.mock(WebSocketSession::class.java)
        `when`(session.attributes).thenReturn(mutableMapOf<String, Any>(Attributes.ID to playerId))
        `when`(session.uri).thenReturn(URI.create(path))
        `when`(session.isOpen).thenAnswer { isOpen() }
        return session
    }

    /**
     * 使用测试调度器创建处理器，确保异步订阅任务可由测试主动推进。
     *
     * @param lobbyService 大厅服务 mock
     * @return 使用测试协程作用域的处理器
     */
    private fun TestScope.lobbySocketHandler(lobbyService: LobbyLifecycleService): LobbySocketHandler {
        return LobbySocketHandler(lobbyService, CoroutineScope(StandardTestDispatcher(testScheduler)))
    }

    /**
     * 捕获 WebSocket 错误响应，验证协议中的错误类型没有被兜底分支覆盖。
     *
     * @param session 已发送错误响应的会话
     * @param errorType 期望返回给客户端的错误类型
     */
    private fun assertSentError(session: WebSocketSession, errorType: ErrorType) {
        val messageCaptor = ArgumentCaptor.forClass(TextMessage::class.java)
        verify(session).sendMessage(messageCaptor.capture())
        assertThat(messageCaptor.value.payload).contains("\"id\":\"$errorType\"")
    }

    /**
     * 验证连接建立成功后处理器向客户端发送大厅快照。
     *
     * @param session 已完成连接建立的会话
     */
    private fun assertSentSnapshot(session: WebSocketSession) {
        val messageCaptor = ArgumentCaptor.forClass(TextMessage::class.java)
        verify(session, atLeastOnce()).sendMessage(messageCaptor.capture())
        assertThat(messageCaptor.allValues.map { it.payload })
            .anySatisfy { payload -> assertThat(payload).contains("\"type\":\"${LobbyMessageType.LOBBY_SNAPSHOT}\"") }
    }

    /**
     * 注册 Mockito 任意玩家匹配器，并返回非空占位对象以避开 Kotlin 参数空值校验。
     *
     * @return 任意玩家匹配器的非空占位对象
     */
    private fun anyLobbyPlayer(): LobbyPlayer {
        Mockito.any(LobbyPlayer::class.java)
        return LobbyPlayer(0L) {}
    }
}

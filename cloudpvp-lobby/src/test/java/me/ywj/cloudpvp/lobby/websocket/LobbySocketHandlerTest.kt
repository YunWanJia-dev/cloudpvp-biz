package me.ywj.cloudpvp.lobby.websocket

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import me.ywj.cloudpvp.core.constant.header.Attributes
import me.ywj.cloudpvp.core.type.SteamID64
import me.ywj.cloudpvp.lobby.entity.LobbyPlayer
import me.ywj.cloudpvp.lobby.service.LobbyService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.web.socket.CloseStatus
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
        val lobbyService = Mockito.mock(LobbyService::class.java)
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
        assertThat(player.steamID64).isEqualTo(VALID_PLAYER_ID)
        assertThat(session.attributes.values).contains(player)
        verify(lobbyService).subscribeLobby(player, 123)

        handler.afterConnectionClosed(session, CloseStatus.NORMAL)

        verify(lobbyService).unsubscribeLobby(player)
        assertThat(session.attributes.values).doesNotContain(player)
    }

    /**
     * 验证频道消息触发的连接关闭回调会先清理订阅状态，再关闭底层 WebSocket。
     */
    @Test
    fun playerCloseConnectionUnsubscribesAndClosesSession() = runTest {
        val lobbyService = Mockito.mock(LobbyService::class.java)
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
        val lobbyService = Mockito.mock(LobbyService::class.java)
        val handler = lobbySocketHandler(lobbyService)

        Mockito.doReturn(false).`when`(lobbyService).subscribeLobby(anyLobbyPlayer(), eq(123))

        val session = lobbySession(VALID_PLAYER_ID, "/ws/123")

        handler.afterConnectionEstablished(session)
        advanceUntilIdle()

        verify(session).close()
        verify(lobbyService, never()).unsubscribeLobby(anyLobbyPlayer())
        assertThat(session.attributes.values).noneMatch { it is LobbyPlayer }
    }

    /**
     * 验证订阅任务完成前连接已关闭时，处理器会补偿取消 Redis 监听器。
     */
    @Test
    fun afterConnectionEstablishedUnsubscribesWhenSessionClosedBeforeSubscribeCompletes() = runTest {
        val lobbyService = Mockito.mock(LobbyService::class.java)
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
     * 验证无效连接参数会在进入服务层前被拒绝。
     */
    @Test
    fun invalidSessionClosesWithoutSubscribing() {
        val lobbyService = Mockito.mock(LobbyService::class.java)
        val handler = LobbySocketHandler(lobbyService)
        val session = lobbySession(VALID_PLAYER_ID, "/ws/not-a-lobby")

        handler.afterConnectionEstablished(session)

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
    private fun TestScope.lobbySocketHandler(lobbyService: LobbyService): LobbySocketHandler {
        return LobbySocketHandler(lobbyService, CoroutineScope(StandardTestDispatcher(testScheduler)))
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

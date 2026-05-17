package me.ywj.cloudpvp.lobby.websocket

import kotlinx.coroutines.runBlocking
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
import java.util.concurrent.atomic.AtomicReference

/**
 * LobbySocketHandlerTest
 * 大厅 WebSocket 处理器单元测试。
 *
 * @author sheip9
 * @since 2026/5/16 18:00
 */
class LobbySocketHandlerTest {
    /**
     * 验证连接建立后订阅目标大厅，并把订阅玩家保存在当前 session 中供断开时清理。
     */
    @Test
    fun afterConnectionEstablishedSubscribesAndStoresPlayerForDisconnect() {
        val lobbyService = Mockito.mock(LobbyService::class.java)
        val handler = LobbySocketHandler(lobbyService)
        val playerId = 76561197960265729L
        val subscribedPlayer = AtomicReference<LobbyPlayer>()

        runBlocking {
            Mockito.doAnswer { invocation ->
                val player = invocation.getArgument<LobbyPlayer>(0)
                player.lobbyId = 123
                subscribedPlayer.set(player)
                true
            }.`when`(lobbyService).subscribeLobby(anyLobbyPlayer(), eq(123))
        }

        val session = lobbySession(playerId, "/ws/123")

        handler.afterConnectionEstablished(session)

        val player = subscribedPlayer.get()
        assertThat(player).isNotNull()
        assertThat(player.steamID64).isEqualTo(playerId)
        assertThat(session.attributes.values).contains(player)
        runBlocking {
            verify(lobbyService).subscribeLobby(player, 123)
        }

        handler.afterConnectionClosed(session, CloseStatus.NORMAL)

        verify(lobbyService).unsubscribeLobby(player)
        assertThat(session.attributes.values).doesNotContain(player)
    }

    /**
     * 验证订阅失败时关闭连接，且不保存需要断开清理的玩家状态。
     */
    @Test
    fun afterConnectionEstablishedClosesWhenSubscribeReturnsFalse() {
        val lobbyService = Mockito.mock(LobbyService::class.java)
        val handler = LobbySocketHandler(lobbyService)

        runBlocking {
            Mockito.doReturn(false).`when`(lobbyService).subscribeLobby(anyLobbyPlayer(), eq(123))
        }

        val session = lobbySession(456L, "/ws/123")

        handler.afterConnectionEstablished(session)

        verify(session).close()
        verify(lobbyService, never()).unsubscribeLobby(anyLobbyPlayer())
        assertThat(session.attributes.values).noneMatch { it is LobbyPlayer }
    }

    /**
     * 验证无效连接参数会在进入服务层前被拒绝。
     */
    @Test
    fun invalidSessionClosesWithoutSubscribing() {
        val lobbyService = Mockito.mock(LobbyService::class.java)
        val handler = LobbySocketHandler(lobbyService)
        val session = lobbySession(456L, "/ws/not-a-lobby")

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
    private fun lobbySession(playerId: SteamID64, path: String): WebSocketSession {
        val session = Mockito.mock(WebSocketSession::class.java)
        `when`(session.attributes).thenReturn(mutableMapOf<String, Any>(Attributes.ID to playerId))
        `when`(session.uri).thenReturn(URI.create(path))
        `when`(session.isOpen).thenReturn(true)
        return session
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

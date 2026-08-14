package me.ywj.cloudpvp.lobby.model

import me.ywj.cloudpvp.core.utils.JacksonUtils
import me.ywj.cloudpvp.core.model.lobby.LobbyStatus
import me.ywj.cloudpvp.lobby.model.messaging.LobbyUpdateMessage
import me.ywj.cloudpvp.lobby.model.messaging.MatchMessage
import me.ywj.cloudpvp.lobby.model.messaging.MatchmakingMatchStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * MatchmakingReturnMessageTest
 * 校验 Go 服务与 Biz 共享的完整 Match JSON 契约。
 *
 * @author sheip9
 * @since 2026/8/13 16:27
 */
class MatchmakingReturnMessageTest {
    private val objectMapper = JacksonUtils.INSTANCE

    /**
     * 验证旧的单大厅状态消息仍可解析。
     */
    @Test
    fun readsLobbyStatusMessage() {
        val message = objectMapper.readValue(
            """{"lobby_id":"123","status":"WAITING","reason":"cancelled"}""",
            LobbyUpdateMessage::class.java,
        )

        assertThat(message.lobbyId).isEqualTo("123")
        assertThat(message.status).isEqualTo(LobbyStatus.WAITING)
        assertThat(message.reason).isEqualTo("cancelled")
    }

    /**
     * 验证 Matcher 的 WAITING_FOR_SERVER 完整比赛消息可解析且 SteamID64 保持字符串精度。
     */
    @Test
    fun readsWaitingForServerMatchMessage() {
        val message = objectMapper.readValue(
            """
                {
                  "match_id":"match-1",
                  "game_mode":"CS2/5v5/competitive",
                  "status":"WAITING_FOR_SERVER",
                  "teams":[{"lobby_ids":["123"],"members":[{"player_id":"76561198999990001"}]}],
                  "server":null,
                  "created_at":"2026-08-13T08:00:00Z",
                  "updated_at":"2026-08-13T08:00:00Z"
                }
            """.trimIndent(),
            MatchMessage::class.java,
        )

        assertThat(message.matchId).isEqualTo("match-1")
        assertThat(message.status).isEqualTo(MatchmakingMatchStatus.WAITING_FOR_SERVER)
        assertThat(message.teams.single().lobbyIds).containsExactly("123")
        assertThat(message.teams.single().members.single().playerId).isEqualTo("76561198999990001")
        assertThat(message.server).isNull()
    }

    /**
     * 验证 Allocator 的 IN_PROGRESS 消息使用同一模型并能解析服务器地址。
     */
    @Test
    fun readsInProgressMatchMessage() {
        val message = objectMapper.readValue(
            """
                {
                  "match_id":"match-1",
                  "game_mode":"CS2/5v5/competitive",
                  "status":"IN_PROGRESS",
                  "teams":[{"lobby_ids":["123"],"members":[{"player_id":"456"}]}],
                  "server":{"ip":"127.0.0.1"},
                  "created_at":"2026-08-13T08:00:00Z",
                  "updated_at":"2026-08-13T08:00:10Z"
                }
            """.trimIndent(),
            MatchMessage::class.java,
        )

        assertThat(message.status).isEqualTo(MatchmakingMatchStatus.IN_PROGRESS)
        assertThat(message.server?.ip).isEqualTo("127.0.0.1")
    }
}

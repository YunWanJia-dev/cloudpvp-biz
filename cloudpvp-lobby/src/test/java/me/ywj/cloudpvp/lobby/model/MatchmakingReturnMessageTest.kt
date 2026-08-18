package me.ywj.cloudpvp.lobby.model

import me.ywj.cloudpvp.core.utils.JacksonUtils
import me.ywj.cloudpvp.core.model.lobby.LobbyStatus
import me.ywj.cloudpvp.lobby.entity.MatchStatus
import me.ywj.cloudpvp.lobby.model.messaging.LobbyUpdateMessage
import me.ywj.cloudpvp.lobby.model.messaging.MatchMessage
import me.ywj.cloudpvp.lobby.model.messaging.MatchmakingMatchStatus
import me.ywj.cloudpvp.lobby.model.messaging.MatchmakingMember
import me.ywj.cloudpvp.lobby.model.messaging.MatchmakingServer
import me.ywj.cloudpvp.lobby.model.messaging.MatchmakingTeam
import me.ywj.cloudpvp.lobby.model.messaging.toEntity
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

    /**
     * 验证完整比赛消息会逐层映射为不依赖消息模型的比赛实体。
     */
    @Test
    fun convertsCompleteMatchMessageToEntity() {
        val match = completeMatchMessage().toEntity()
        val waitingMatch = completeMatchMessage().copy(
            status = MatchmakingMatchStatus.WAITING_FOR_SERVER,
            server = null,
        ).toEntity()

        assertThat(match.matchId).isEqualTo("match-1")
        assertThat(match.gameMode).isEqualTo("CS2/5v5/competitive")
        assertThat(match.status).isEqualTo(MatchStatus.IN_PROGRESS)
        assertThat(match.teams).hasSize(2)
        assertThat(match.teams[0].lobbyIds).containsExactly("123", "124")
        assertThat(match.teams[0].members.map { it.playerId })
            .containsExactly("76561198999990001", "76561198999990002")
        assertThat(match.teams[1].lobbyIds).containsExactly("125")
        assertThat(match.teams[1].members.single().playerId).isEqualTo("76561198999990003")
        assertThat(match.server?.ip).isEqualTo("127.0.0.1")
        assertThat(match.createdAt).isEqualTo("2026-08-13T08:00:00Z")
        assertThat(match.updatedAt).isEqualTo("2026-08-13T08:00:10Z")
        assertThat(waitingMatch.status).isEqualTo(MatchStatus.WAITING_FOR_SERVER)
        assertThat(waitingMatch.server).isNull()
    }

    /**
     * 验证比赛实体序列化后继续使用跨服务约定的 snake_case 字段名。
     */
    @Test
    fun writesMatchEntityWithSnakeCaseContract() {
        val json = objectMapper.readTree(objectMapper.writeValueAsString(completeMatchMessage().toEntity()))
        val firstTeam = json.path("teams").path(0)
        val firstMember = firstTeam.path("members").path(0)

        assertThat(json.path("match_id").asText()).isEqualTo("match-1")
        assertThat(json.path("game_mode").asText()).isEqualTo("CS2/5v5/competitive")
        assertThat(firstTeam.path("lobby_ids").path(0).asText()).isEqualTo("123")
        assertThat(firstMember.path("player_id").asText()).isEqualTo("76561198999990001")
        assertThat(json.path("created_at").asText()).isEqualTo("2026-08-13T08:00:00Z")
        assertThat(json.path("updated_at").asText()).isEqualTo("2026-08-13T08:00:10Z")
        assertThat(json.has("matchId")).isFalse()
        assertThat(json.has("gameMode")).isFalse()
        assertThat(firstTeam.has("lobbyIds")).isFalse()
        assertThat(firstMember.has("playerId")).isFalse()
        assertThat(json.has("createdAt")).isFalse()
        assertThat(json.has("updatedAt")).isFalse()
    }

    /**
     * 创建覆盖完整比赛状态和多队伍嵌套结构的测试消息。
     *
     * @return 用于实体映射与序列化契约测试的比赛消息
     */
    private fun completeMatchMessage(): MatchMessage {
        return MatchMessage(
            matchId = "match-1",
            gameMode = "CS2/5v5/competitive",
            status = MatchmakingMatchStatus.IN_PROGRESS,
            teams = listOf(
                MatchmakingTeam(
                    lobbyIds = listOf("123", "124"),
                    members = listOf(
                        MatchmakingMember("76561198999990001"),
                        MatchmakingMember("76561198999990002"),
                    ),
                ),
                MatchmakingTeam(
                    lobbyIds = listOf("125"),
                    members = listOf(MatchmakingMember("76561198999990003")),
                ),
            ),
            server = MatchmakingServer("127.0.0.1"),
            createdAt = "2026-08-13T08:00:00Z",
            updatedAt = "2026-08-13T08:00:10Z",
        )
    }
}

package me.ywj.cloudpvp.lobby.model

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * MatchmakingLobbyStatusMessage
 * matcher 回传的单个大厅状态消息。
 *
 * @author sheip9
 * @since 2026/8/13 16:27
 */
data class MatchmakingLobbyStatusMessage(
    @field:JsonProperty("lobby_id")
    val lobbyId: String,
    val status: MatchmakingLobbyStatus,
    val reason: String? = null,
)

/**
 * MatchmakingLobbyStatus
 * matcher 可回传给大厅服务的状态类型。
 *
 * @author sheip9
 * @since 2026/8/13 16:27
 */
enum class MatchmakingLobbyStatus {
    WAITING,
    MATCHING,
}

/**
 * MatchmakingMatchMessage
 * Matcher 与 Allocator 共享的完整比赛生命周期消息。
 *
 * @author sheip9
 * @since 2026/8/13 16:27
 */
data class MatchmakingMatchMessage(
    @field:JsonProperty("match_id")
    val matchId: String,
    @field:JsonProperty("game_mode")
    val gameMode: String,
    val status: MatchmakingMatchStatus,
    val teams: List<MatchmakingTeam>,
    val server: MatchmakingServer? = null,
    @field:JsonProperty("created_at")
    val createdAt: String,
    @field:JsonProperty("updated_at")
    val updatedAt: String,
)

/**
 * MatchmakingMatchStatus
 * 完整比赛消息允许的生命周期状态。
 *
 * @author sheip9
 * @since 2026/8/13 17:21
 */
enum class MatchmakingMatchStatus {
    WAITING_FOR_SERVER,
    IN_PROGRESS,
}

/**
 * MatchmakingTeam
 * 一支匹配队伍的大厅与成员快照。
 *
 * @author sheip9
 * @since 2026/8/13 17:21
 */
data class MatchmakingTeam(
    @field:JsonProperty("lobby_ids")
    val lobbyIds: List<String>,
    val members: List<MatchmakingMember>,
)

/**
 * MatchmakingMember
 * 比赛成员快照。
 *
 * @author sheip9
 * @since 2026/8/13 17:21
 */
data class MatchmakingMember(
    @field:JsonProperty("player_id")
    val playerId: String,
)

/**
 * MatchmakingServer
 * Allocator 分配完成后的服务器地址。
 *
 * @author sheip9
 * @since 2026/8/13 17:21
 */
data class MatchmakingServer(
    val ip: String,
)

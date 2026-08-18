package me.ywj.cloudpvp.lobby.model.messaging

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSetter
import com.fasterxml.jackson.annotation.Nulls
import me.ywj.cloudpvp.lobby.entity.Match
import me.ywj.cloudpvp.lobby.entity.MatchMember
import me.ywj.cloudpvp.lobby.entity.MatchServer
import me.ywj.cloudpvp.lobby.entity.MatchStatus
import me.ywj.cloudpvp.lobby.entity.MatchTeam

/**
 * MatchMessage
 * Matcher 与 Allocator 共享的完整比赛生命周期消息。
 *
 * @author sheip9
 * @since 2026/8/13 16:27
 */
data class MatchMessage(
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
    // 兼容上游显式发送 null，并将缺失成员统一为空列表。
    @param:JsonSetter(nulls = Nulls.AS_EMPTY)
    val members: List<MatchmakingMember> = emptyList(),
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

/**
 * 将完整比赛生命周期消息转换为大厅模块的比赛实体。
 *
 * @return 字段完整映射后的比赛实体
 */
fun MatchMessage.toEntity(): Match {
    return Match(
        matchId = matchId,
        gameMode = gameMode,
        status = when (status) {
            MatchmakingMatchStatus.WAITING_FOR_SERVER -> MatchStatus.WAITING_FOR_SERVER
            MatchmakingMatchStatus.IN_PROGRESS -> MatchStatus.IN_PROGRESS
        },
        teams = teams.map { team ->
            MatchTeam(
                lobbyIds = team.lobbyIds.toList(),
                members = team.members.map { member -> MatchMember(member.playerId) },
            )
        },
        server = server?.let { MatchServer(it.ip) },
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

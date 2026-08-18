package me.ywj.cloudpvp.lobby.entity

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.data.annotation.Id
import org.springframework.data.redis.core.RedisHash

/**
 * Match
 * Redis 中持久化的完整比赛状态。
 *
 * @property matchId 比赛唯一标识
 * @property gameMode 比赛使用的完整游戏模式标识
 * @property status 当前比赛生命周期状态
 * @property teams 参加比赛的队伍快照
 * @property server 已分配的比赛服务器，尚未分配时为 null
 * @property createdAt 上游提供的比赛创建时间字符串
 * @property updatedAt 上游提供的比赛更新时间字符串
 * @author sheip9
 * @since 2026/8/17 17:43
 */
@RedisHash("Match")
data class Match(
    @Id
    @field:JsonProperty("match_id")
    val matchId: String,
    @field:JsonProperty("game_mode")
    val gameMode: String,
    val status: MatchStatus,
    val teams: List<MatchTeam>,
    val server: MatchServer? = null,
    @field:JsonProperty("created_at")
    val createdAt: String,
    @field:JsonProperty("updated_at")
    val updatedAt: String,
)

/**
 * MatchStatus
 * 比赛在大厅模块内使用的生命周期状态。
 *
 * @author sheip9
 * @since 2026/8/17 17:43
 */
enum class MatchStatus {
    WAITING_FOR_SERVER,
    IN_PROGRESS,
}

/**
 * MatchTeam
 * 比赛队伍及其大厅、成员快照。
 *
 * @property lobbyIds 组成当前队伍的大厅 ID 列表
 * @property members 当前队伍的成员列表
 * @author sheip9
 * @since 2026/8/17 17:43
 */
data class MatchTeam(
    @field:JsonProperty("lobby_ids")
    val lobbyIds: List<String>,
    val members: List<MatchMember>,
)

/**
 * MatchMember
 * 比赛成员快照。
 *
 * @property playerId 玩家唯一标识
 * @author sheip9
 * @since 2026/8/17 17:43
 */
data class MatchMember(
    @field:JsonProperty("player_id")
    val playerId: String,
)

/**
 * MatchServer
 * 分配给比赛的服务器信息。
 *
 * @property ip 客户端连接比赛服务器所使用的地址
 * @author sheip9
 * @since 2026/8/17 17:43
 */
data class MatchServer(
    val ip: String,
)

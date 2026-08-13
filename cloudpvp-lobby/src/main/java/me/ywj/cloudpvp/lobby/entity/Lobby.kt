package me.ywj.cloudpvp.lobby.entity

import com.fasterxml.jackson.annotation.JsonIgnore
import me.ywj.cloudpvp.core.model.lobby.LobbyStatus
import me.ywj.cloudpvp.core.type.SteamID64
import me.ywj.cloudpvp.lobby.model.MatchmakingMatchStatus
import org.springframework.data.annotation.Id
import org.springframework.data.redis.core.RedisHash
import java.time.Instant

/**
 * Lobby
 *
 * @author sheip9
 * @since 2024/10/20 16:38
 */
@RedisHash("Lobby")
data class Lobby(
    @Id val id: Int,
    var players: ArrayList<Long>?,
) {
    var host: SteamID64 = 0
    var status: LobbyStatus = LobbyStatus.WAITING
    var gameKey: String? = null
    var typeKey: String? = null
    var modeKey: String? = null
    var matchId: String? = null

    // 这三个内部游标会随 Lobby 持久化，用于阻止 MQ 乱序消息回退比赛状态。
    @JsonIgnore
    var matchMessageId: String? = null

    @JsonIgnore
    var matchMessageStatus: MatchmakingMatchStatus? = null

    @JsonIgnore
    var matchMessageUpdatedAt: Instant? = null

    constructor(id: Int) : this(id, ArrayList<Long>())

    init {
        if (players == null) {
            players = ArrayList<Long>()
        }
    }
}

package me.ywj.cloudpvp.lobby.model

import com.fasterxml.jackson.annotation.JsonProperty
import me.ywj.cloudpvp.lobby.entity.Lobby

/**
 * MatchmakingLobbyMessage
 * lobby 服务发送给 matcher 的最小匹配请求模型。
 *
 * @author sheip9
 * @since 2026/8/12 14:31
 */
data class MatchmakingLobbyMessage(
    @field:JsonProperty("lobby_id")
    val lobbyId: String,
    @field:JsonProperty("game_mode")
    val gameMode: String,
    val members: List<MatchmakingPlayerMessage>,
) {
    companion object {
        private const val CSGO_5V5_GAME_MODE = "matchmaker/5v5/competitive"

        /**
         * 将业务大厅转换为 matcher 当前支持的 mock 请求。
         *
         * matcher 目前只实现一个 5v5 模式，因此通信打通阶段统一映射到该模式。
         *
         * @param lobby 已完成模式选择的业务大厅
         * @return matcher 可直接消费的匹配请求
         */
        fun from(lobby: Lobby): MatchmakingLobbyMessage {
            return MatchmakingLobbyMessage(
                lobbyId = lobby.id.toString(),
                gameMode = CSGO_5V5_GAME_MODE,
                members = lobby.players.orEmpty().map { MatchmakingPlayerMessage(it.toString()) },
            )
        }
    }
}

/**
 * MatchmakingPlayerMessage
 * matcher 匹配请求中的玩家信息。
 *
 * @author sheip9
 * @since 2026/8/12 14:31
 */
data class MatchmakingPlayerMessage(
    @field:JsonProperty("player_id")
    val playerId: String,
)

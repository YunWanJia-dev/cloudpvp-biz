package me.ywj.cloudpvp.lobby.model

import com.fasterxml.jackson.annotation.JsonProperty
import me.ywj.cloudpvp.lobby.entity.Lobby

/**
 * LobbyEnqueueMessage
 * lobby 服务发送给 matcher 的最小匹配请求模型。
 *
 * @author sheip9
 * @since 2026/8/12 14:31
 */
data class LobbyEnqueueMessage(
    @field:JsonProperty("lobby_id")
    val lobbyId: String,
    @field:JsonProperty("game_mode")
    val gameMode: String,
    @field:JsonProperty("player_count")
    val playerCount: Int,
) {
    companion object {
        /**
         * 将已完成模式选择的业务大厅转换为 matcher 请求。
         *
         * @param lobby 已完成模式选择的业务大厅
         * @return matcher 可直接消费的匹配请求
         */
        fun from(lobby: Lobby) = LobbyEnqueueMessage(
            lobbyId = lobby.id.toString(),
            gameMode = lobby.gameMode,
            playerCount = lobby.players.orEmpty().size,
        )
    }
}

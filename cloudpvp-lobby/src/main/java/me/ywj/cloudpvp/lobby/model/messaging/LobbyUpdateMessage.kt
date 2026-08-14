package me.ywj.cloudpvp.lobby.model.messaging

import com.fasterxml.jackson.annotation.JsonProperty
import me.ywj.cloudpvp.core.model.lobby.LobbyStatus

/**
 * Matcher 回传的单个大厅状态消息。
 *
 * @author sheip9
 * @since 2026/8/13 16:27
 */
data class LobbyUpdateMessage(
    @field:JsonProperty("lobby_id")
    val lobbyId: String,
    val status: LobbyStatus,
    val reason: String? = null,
)

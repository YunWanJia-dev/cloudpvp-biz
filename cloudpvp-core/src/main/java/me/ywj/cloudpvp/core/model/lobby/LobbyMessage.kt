package me.ywj.cloudpvp.core.model.lobby

import me.ywj.cloudpvp.core.type.SteamId64

/**
 * LobbyMessage
 *
 * @author sheip9
 * @since 2024/10/24 15:30
 */
data class LobbyMessage(
    val type: LobbyMessageType,
) {
    var playerId: SteamId64? = null
    var content: String? = null
}

enum class LobbyMessageType {
    JOIN,
    LEAVE,
    TEXTING
}

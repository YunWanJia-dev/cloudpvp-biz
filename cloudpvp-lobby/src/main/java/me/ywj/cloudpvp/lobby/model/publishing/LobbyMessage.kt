package me.ywj.cloudpvp.lobby.model.publishing

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonTypeInfo
import me.ywj.cloudpvp.core.type.SteamID64

/**
 * LobbyMessage
 *
 * @author sheip9
 * @since 2024/10/24 15:30
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY, property = "@class")
data class LobbyMessage(
    val type: LobbyMessageType,
    val actionPlayerId: SteamID64?,
    val data: String,
)

enum class LobbyMessageType {
    JOIN,
    LEAVE,
    TEXTING,
    UPDATE_HOST,
}

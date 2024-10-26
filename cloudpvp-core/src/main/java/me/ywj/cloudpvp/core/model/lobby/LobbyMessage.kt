package me.ywj.cloudpvp.core.model.lobby

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
) {
    @JsonCreator
    constructor(@JsonProperty("type") type: LobbyMessageType, @JsonProperty("playerId") playerId: SteamID64?, @JsonProperty("content") content: String?) :  this(type) {
        playerId?.let { this.playerId = it }
        content?.let { this.content = it }
    }
    var playerId: SteamID64? = null
    var content: String? = null
}

enum class LobbyMessageType {
    JOIN,
    LEAVE,
    TEXTING,
    UPDATE_HOST
}

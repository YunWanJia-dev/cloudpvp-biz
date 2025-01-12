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
    constructor(@JsonProperty("type") type: LobbyMessageType, @JsonProperty("data") data: Any) : this(type) {
        this.data = data
    }

    var data: Any? = null
}

enum class LobbyMessageType {
    JOIN,
    LEAVE,
    TEXTING,
    UPDATE_HOST,
    PLAYER_LIST
}

data class LobbyMessageDataTexting(val playerID64: SteamID64, val content: String)
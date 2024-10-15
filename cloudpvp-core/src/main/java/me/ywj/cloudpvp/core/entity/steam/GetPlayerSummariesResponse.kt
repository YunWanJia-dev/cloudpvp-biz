package me.ywj.cloudpvp.core.entity.steam

import com.fasterxml.jackson.annotation.JsonProperty
import me.ywj.cloudpvp.core.entity.PlayerSummary

data class GetPlayerSummariesResponse (
    @get:JsonProperty(required=true)@field:JsonProperty(required=true)
    val players: List<PlayerSummary>
)
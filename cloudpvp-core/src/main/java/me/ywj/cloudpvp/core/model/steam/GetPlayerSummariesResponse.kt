package me.ywj.cloudpvp.core.model.steam

import com.fasterxml.jackson.annotation.JsonProperty

data class GetPlayerSummariesResponse(
    @get:JsonProperty(required = true) @field:JsonProperty(required = true)
    val players: List<PlayerSummary>,
)
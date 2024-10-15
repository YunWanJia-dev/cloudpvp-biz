package me.ywj.cloudpvp.core.entity.steam

import com.fasterxml.jackson.annotation.JsonProperty

data class SteamResponse<T>(
    @get:JsonProperty(required=true)@field:JsonProperty(required=true)
    val response : T,
)

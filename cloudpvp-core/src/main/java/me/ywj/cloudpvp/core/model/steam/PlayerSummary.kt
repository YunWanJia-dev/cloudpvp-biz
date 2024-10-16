package me.ywj.cloudpvp.core.model.steam

import com.fasterxml.jackson.annotation.JsonProperty

data class PlayerSummary (
    val avatar: String,
    @JsonProperty("avatarfull")
    val avatarFull: String,
    @JsonProperty("avatarhash")
    val avatarHash: String,
    @JsonProperty("avatarmedium")
    val avatarMedium: String,
    @JsonProperty("communityvisibilitystate")
    val communityVisibilityState: CommunityVisibilityStateEnum,
    @JsonProperty("loccityid")
    val locCityId: Long,
    @JsonProperty("loccountrycode")
    val locCountryCode: String,
    @JsonProperty("locstatecode")
    val locStateCode: String,
    @JsonProperty("personaname")
    val personaName: String,
    @JsonProperty("personastate")
    val personaState: Long,
    @JsonProperty("personastateflags")
    val personaStateFlags: Long,
    @JsonProperty("primaryclanid")
    val primaryClanId: String,
    @JsonProperty("profilestate")
    val profileState: Long,
    @JsonProperty("profileurl")
    val profileUrl: String,
    @JsonProperty("realname")
    val realName: String,
    @JsonProperty("steamid")
    val steamId: ULong,
    @JsonProperty("timecreated")
    val timeCreated: Long,
) 
enum class CommunityVisibilityStateEnum(val value: Number) {
    PRIVATE(1),
    PUBLIC(3),
}
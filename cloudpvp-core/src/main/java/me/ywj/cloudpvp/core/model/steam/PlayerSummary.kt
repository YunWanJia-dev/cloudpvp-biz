package me.ywj.cloudpvp.core.model.steam

import com.fasterxml.jackson.annotation.JsonProperty
import me.ywj.cloudpvp.core.type.SteamId64

data class PlayerSummary (
    val avatar: String,
    @JsonProperty("avatarfull")
    val avatarFull: String,
    @JsonProperty("avatarhash")
    val avatarHash: String,
    @JsonProperty("avatarmedium")
    val avatarMedium: String,
    @JsonProperty("commentpermission")
    val commentPermission: Boolean?,
    @JsonProperty("communityvisibilitystate")
    val communityVisibilityState: CommunityVisibilityStateEnum,
    @JsonProperty("gameid")
    val gameId: Long?,
    @JsonProperty("gameserverip")
    val gameServerIp: String?,
    @JsonProperty("gameextrainfo")
    val gameExtraInfo: String?,
    @JsonProperty("lastlogoff")
    val lastLogoff: Long,
    @JsonProperty("loccityid")
    val locCityId: Long?,
    @JsonProperty("loccountrycode")
    val locCountryCode: String?,
    @JsonProperty("locstatecode")
    val locStateCode: String?,
    @JsonProperty("personaname")
    val personaName: String,
    @JsonProperty("personastate")
    val personaState: PersonaStateEnum,
    @JsonProperty("personastateflags")
    val personaStateFlags: Byte,
    @JsonProperty("primaryclanid")
    val primaryClanId: String,
    @JsonProperty("profilestate")
    val profileState: Boolean?,
    @JsonProperty("profileurl")
    val profileUrl: String,
    @JsonProperty("realname")
    val realName: String?,
    @JsonProperty("steamid")
    val steamId: SteamId64,
    @JsonProperty("timecreated")
    val timeCreated: Long,
) 
enum class PersonaStateEnum(val state: Byte) {
    OFFLINE(0),
    ONLINE(1),
    BUSY(2),
    AWAY(3),
    SNOOZE(4),
    LOOKING_TO_TRADE(5),
    LOOKING_TO_PLAY(6)
}
enum class CommunityVisibilityStateEnum(val value: Byte) {
    PRIVATE(1),
    PUBLIC(3),
}
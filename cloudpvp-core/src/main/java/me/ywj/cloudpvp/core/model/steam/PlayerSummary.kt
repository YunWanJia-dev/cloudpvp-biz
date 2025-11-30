package me.ywj.cloudpvp.core.model.steam

import com.fasterxml.jackson.annotation.JsonProperty
import me.ywj.cloudpvp.core.type.SteamID64

data class PlayerSummary(
    val avatar: String,
    @field:JsonProperty("avatarfull")
    val avatarFull: String,
    @field:JsonProperty("avatarhash")
    val avatarHash: String,
    @field:JsonProperty("avatarmedium")
    val avatarMedium: String,
    @field:JsonProperty("commentpermission")
    val commentPermission: Boolean?,
    @field:JsonProperty("communityvisibilitystate")
    val communityVisibilityState: CommunityVisibilityStateEnum,
    @field:JsonProperty("gameid")
    val gameId: Long?,
    @field:JsonProperty("gameserverip")
    val gameServerIp: String?,
    @field:JsonProperty("gameextrainfo")
    val gameExtraInfo: String?,
    @field:JsonProperty("lastlogoff")
    val lastLogoff: Long,
    @field:JsonProperty("loccityid")
    val locCityId: Long?,
    @field:JsonProperty("loccountrycode")
    val locCountryCode: String?,
    @field:JsonProperty("locstatecode")
    val locStateCode: String?,
    @field:JsonProperty("personaname")
    val personaName: String,
    @field:JsonProperty("personastate")
    val personaState: PersonaStateEnum,
    @field:JsonProperty("personastateflags")
    val personaStateFlags: Byte,
    @field:JsonProperty("primaryclanid")
    val primaryClanId: String,
    @field:JsonProperty("profilestate")
    val profileState: Boolean?,
    @field:JsonProperty("profileurl")
    val profileUrl: String,
    @field:JsonProperty("realname")
    val realName: String?,
    @field:JsonProperty("steamid")
    val steamId: SteamID64,
    @field:JsonProperty("timecreated")
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
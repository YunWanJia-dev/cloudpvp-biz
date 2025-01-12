package me.ywj.cloudpvp.core.entity

import me.ywj.cloudpvp.core.type.SteamID64
import java.util.*

/**
 * PlayerProfile
 *
 * @author sheip9
 * @since 2024/10/16 11:49
 */
class PlayerProfile(
    val steamID64: SteamID64,
    val name: String,
    val avatarLink: String,
) {
    var auditAt: Date? = null
}
package me.ywj.cloudpvp.core.entity

import me.ywj.cloudpvp.core.type.SteamId64

/**
 * PlayerProfile
 *
 * @author sheip9
 * @since 2024/10/16 11:49
 */
class PlayerProfile (
    val steamId64: SteamId64,
    val name: String,
    val avatarLink: String,
) {
}
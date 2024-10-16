package me.ywj.cloudpvp.core.entity

import me.ywj.cloudpvp.core.type.SteamId

/**
 * PlayerProfile
 *
 * @author sheip9
 * @since 2024/10/16 11:49
 */
class PlayerProfile (
    val steamId: SteamId,
    val name: String,
    val avatarLink: String,
) {
}
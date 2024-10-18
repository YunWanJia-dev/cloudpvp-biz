package me.ywj.cloudpvp.core.utils

import me.ywj.cloudpvp.core.constant.steam.SteamUser
import me.ywj.cloudpvp.core.type.SteamId

/**
 * PlayerUtils
 *
 * @author sheip9
 * @since 2024/10/19 00:24
 */
object PlayerUtils {
    fun checkIdIsValid(steamId : SteamId?): Boolean {
        return steamId != null && steamId >= SteamUser.MinimalId
    }
}
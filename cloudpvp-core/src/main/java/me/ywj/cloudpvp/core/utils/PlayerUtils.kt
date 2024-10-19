package me.ywj.cloudpvp.core.utils

import me.ywj.cloudpvp.core.constant.steam.SteamUser
import me.ywj.cloudpvp.core.type.SteamId64

/**
 * PlayerUtils
 *
 * @author sheip9
 * @since 2024/10/19 00:24
 */
object PlayerUtils {
    fun checkIdIsValid(steamId64 : SteamId64?): Boolean {
        return steamId64 != null && steamId64 >= SteamUser.MinimalId
    }
}
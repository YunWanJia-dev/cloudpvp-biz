package me.ywj.cloudpvp.core.utils

import me.ywj.cloudpvp.core.constant.steam.SteamUser
import me.ywj.cloudpvp.core.type.SteamID
import me.ywj.cloudpvp.core.type.SteamID3
import me.ywj.cloudpvp.core.type.SteamID64

/**
 * PlayerUtils
 * 玩家工具类
 * @author sheip9
 * @since 2024/10/19 00:24
 */
object PlayerUtils {
    /**
     * checkIdIsValid
     * 校验玩家SteamID64有效性
     * @param steamId64 SteamID64
     * @return id是否有校
     */
    fun checkIdIsValid(steamId64 : SteamID64?): Boolean {
        return steamId64 != null && steamId64 > SteamUser.MINIMAL_ID_64
    }

    /**
     * convertToSteamID3
     * 将64位的SteamID转换成steamID3
     */
    fun convertToSteamID3(steamId64 : SteamID64?) : SteamID3 {
        TODO()
    }

    /**
     * convertToSteamID
     * 将64位的SteamID转换成steamID3
     */
    fun convertToSteamID(steamId64 : SteamID64?) : SteamID {
        TODO()
    }
}
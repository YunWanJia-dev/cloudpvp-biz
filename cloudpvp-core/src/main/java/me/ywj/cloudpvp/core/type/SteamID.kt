package me.ywj.cloudpvp.core.type

/**
 * SteamId
 *
 * @author sheip9
 * @since 2024/10/16 16:48
 */
typealias SteamID64 = Long

typealias SteamID3 = Int

typealias SteamID = Object //TODO 原生的SteamID类型 如：STEAM_0:0:441169418

fun String.toSteamID64() : SteamID64? {
    return this.toLongOrNull()
}
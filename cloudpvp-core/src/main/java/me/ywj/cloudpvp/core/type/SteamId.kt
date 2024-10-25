package me.ywj.cloudpvp.core.type

/**
 * SteamId
 *
 * @author sheip9
 * @since 2024/10/16 16:48
 */
typealias SteamID64 = Long

typealias SteamID3 = Int

fun String.toSteamId64() : SteamID64? {
    return this.toLongOrNull()
}
package me.ywj.cloudpvp.core.type

/**
 * LobbyId
 *  <br>
 *  目前LobbyId为8位随机数，故用Int类型
 * @author sheip9
 * @since 2024/10/24 14:01
 */
typealias LobbyId = Int

fun String.toLobbyId() : LobbyId {
    return this.toInt()
}
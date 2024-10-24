package me.ywj.cloudpvp.core.constant.lobby

import jdk.internal.joptsimple.internal.Strings

/**
 * LobbyConstant
 *
 * @author sheip9
 * @since 2024/10/20 17:23
 */
object LobbyConstant {
    const val CREATE_TIMEOUT = 10L
    const val ID_SIZE = 8
    val MAXIMUM_ID = Strings.repeat('9', ID_SIZE).toInt()
}
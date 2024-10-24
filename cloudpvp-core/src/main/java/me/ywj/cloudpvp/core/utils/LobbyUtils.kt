package me.ywj.cloudpvp.core.utils

import me.ywj.cloudpvp.core.constant.lobby.LobbyConstant
import me.ywj.cloudpvp.core.type.LobbyId
import me.ywj.cloudpvp.core.type.toLobbyId

/**
 * LobbyUtils
 *
 * @author sheip9
 * @since 2024/10/24 16:00
 */
object LobbyUtils {
    fun generateLobbyId(): LobbyId {
        return RandomUtils().buildRandomNumString(LobbyConstant.ID_SIZE).toLobbyId()
    }
    
    fun checkLobbyIdIsValid(id: LobbyId): Boolean {
        return id > 0 && id <= LobbyConstant.MAXIMUM_ID
    }
}
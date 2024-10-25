package me.ywj.cloudpvp.core.utils

import me.ywj.cloudpvp.core.constant.lobby.LobbyConstant
import me.ywj.cloudpvp.core.type.LobbyId
import me.ywj.cloudpvp.core.type.toLobbyId

/**
 * LobbyUtils
 * 大厅工具类
 * @author sheip9
 * @since 2024/10/24 16:00
 */
object LobbyUtils {
    /**
     * generateLobbyId
     * 生成长度为 [LobbyConstant.ID_SIZE] 的大厅Id
     * @return 生成的大厅Id
     */
    fun generateLobbyId(): LobbyId {
        return RandomUtils().buildRandomNumString(LobbyConstant.ID_SIZE).toLobbyId()!!
    }

    /**
     * checkLobbyIdIsValid
     * 校验大厅Id有效性
     * @param id lobbyId
     * @return id是否有校
     */
    fun checkLobbyIdIsValid(id: LobbyId): Boolean {
        return id > 0 && id <= LobbyConstant.MAXIMUM_ID
    }
}
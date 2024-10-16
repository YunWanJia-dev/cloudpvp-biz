package me.ywj.cloudpvp.cloudpvpstate.entity

import me.ywj.cloudpvp.cloudpvpstate.constant.StateEnum
import me.ywj.cloudpvp.core.entity.BasicPlayer
import me.ywj.cloudpvp.core.type.SteamId

/**
 * PlayerState
 *
 * @author sheip9
 * @since 2024/10/16 16:40
 */
class PlayerState(steamId : SteamId) : BasicPlayer(steamId) {
    var playerState : StateEnum = StateEnum.UNKNOWN
    set(value) {
        field = value
        
    }
}
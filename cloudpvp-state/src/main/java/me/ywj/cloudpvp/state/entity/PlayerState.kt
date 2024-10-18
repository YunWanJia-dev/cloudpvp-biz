package me.ywj.cloudpvp.state.entity

import me.ywj.cloudpvp.core.entity.BasicPlayer
import me.ywj.cloudpvp.core.type.SteamId
import me.ywj.cloudpvp.state.constant.StateEnum
import org.springframework.data.annotation.Id
import org.springframework.data.redis.core.RedisHash

/**
 * PlayerState
 *
 * @author sheip9
 * @since 2024/10/16 16:40
 */
@RedisHash("people")
class PlayerState(@Id val steamId : SteamId)  {
    var state : StateEnum = StateEnum.UNKNOWN
}
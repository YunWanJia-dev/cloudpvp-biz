package me.ywj.cloudpvp.state.entity

import me.ywj.cloudpvp.core.entity.BasicPlayer
import me.ywj.cloudpvp.core.type.SteamID64
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
class PlayerState(@Id override val steamID64 : SteamID64) : BasicPlayer(steamID64)  {
    var state : StateEnum = StateEnum.UNKNOWN
}
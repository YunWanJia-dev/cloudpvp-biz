package me.ywj.cloudpvp.lobby.entity

import org.springframework.data.annotation.Id
import org.springframework.data.redis.core.RedisHash

/**
 * Lobby
 *
 * @author sheip9
 * @since 2024/10/20 16:38
 */
@RedisHash("Lobby")

class Lobby {
    constructor() //垃圾repository能不能支持全参构造函数啊
    constructor(
        id : Int,
    ) {
        this.id = id
    }

    @Id
    var id: Int? = null
        set (value) {
            if (field != null) {
                return
            }
            field = value
        }
    var players : ArrayList<Long>? = null
        set (value) {
            if (field != null) {
                return
            }
            field = value
        }
}

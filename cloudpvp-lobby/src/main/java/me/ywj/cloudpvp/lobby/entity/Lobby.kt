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
data class Lobby(
    @Id val id: Int,
    var players : ArrayList<Long>?
) {
    constructor(id: Int) : this(id, ArrayList<Long>())
    init {
        if (players == null) {
            players = ArrayList<Long>()
        }
    }
}

package me.ywj.cloudpvp.lobby.entity

import me.ywj.cloudpvp.core.entity.BasicPlayer
import me.ywj.cloudpvp.core.type.SteamId64
import org.springframework.data.annotation.Id
import org.springframework.data.redis.core.RedisHash

/**
 * LobbyPlayer
 *
 * @author sheip9
 * @since 2024/10/20 16:31
 */
@RedisHash("LobbyPlayer")
class LobbyPlayer(@Id override val steamId64 : SteamId64) : BasicPlayer(steamId64)  {
    
}
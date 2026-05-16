package me.ywj.cloudpvp.usersummary.entity

import me.ywj.cloudpvp.core.type.SteamID64
import org.springframework.data.annotation.Id
import org.springframework.data.redis.core.RedisHash

/**
 * PlayerProfile
 * 玩家资料基础模型。
 *
 * @author sheip9
 * @since 2024/10/16 11:49
 */
@RedisHash("PlayerProfile", timeToLive = 24 * 60 * 60L)
data class PlayerProfile(
    @Id val steamID64: SteamID64,
    val name: String,
    val avatarLink: String,
)
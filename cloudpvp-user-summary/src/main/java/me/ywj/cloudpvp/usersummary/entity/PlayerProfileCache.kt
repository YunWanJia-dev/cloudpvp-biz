package me.ywj.cloudpvp.usersummary.entity

import me.ywj.cloudpvp.core.entity.PlayerProfile
import me.ywj.cloudpvp.core.type.SteamID64
import org.springframework.data.annotation.Id
import org.springframework.data.redis.core.RedisHash
import org.springframework.data.redis.core.TimeToLive
import java.util.Date

/**
 * PlayerProfileCache
 * Redis 中缓存的玩家资料。
 *
 * @author sheip9
 * @since 2026/5/15 16:53
 */
@RedisHash("PlayerProfile")
class PlayerProfileCache(
    @Id override val steamID64: SteamID64,
    override val name: String,
    override val avatarLink: String,
    override var auditAt: Date? = Date(),
    // Steam 资料会被玩家主动修改，缓存必须自然过期，避免长期返回旧昵称或头像。
    @TimeToLive var ttl: Long = DEFAULT_TTL_SECONDS,
) : PlayerProfile(steamID64, name, avatarLink) {
    companion object {
        private const val DEFAULT_TTL_SECONDS = 24 * 60 * 60L
    }
}

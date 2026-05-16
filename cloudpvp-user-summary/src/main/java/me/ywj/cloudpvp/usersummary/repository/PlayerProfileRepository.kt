package me.ywj.cloudpvp.usersummary.repository

import me.ywj.cloudpvp.usersummary.entity.PlayerProfile
import org.springframework.data.repository.CrudRepository

/**
 * PlayerProfileRepository
 * 玩家资料 Redis 缓存仓库。
 *
 * @author sheip9
 * @since 2026/5/15 16:53
 */
interface PlayerProfileRepository : CrudRepository<PlayerProfile, Long>

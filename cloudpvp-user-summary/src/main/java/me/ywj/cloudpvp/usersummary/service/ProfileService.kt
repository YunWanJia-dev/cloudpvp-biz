package me.ywj.cloudpvp.usersummary.service

import me.ywj.cloudpvp.core.constant.steam.SteamUser
import me.ywj.cloudpvp.core.type.SteamID64
import me.ywj.cloudpvp.usersummary.entity.PlayerProfile
import me.ywj.cloudpvp.core.utils.SteamApiUtils
import me.ywj.cloudpvp.usersummary.repository.PlayerProfileRepository
import org.springframework.stereotype.Service

/**
 * ProfileService
 * 玩家资料查询服务。
 *
 * @author sheip9
 * @since 2024/10/20 18:30
 */
@Service
class ProfileService(
    private val profileRepository: PlayerProfileRepository,
    private val steamApiUtils: SteamApiUtils,
) {
    /**
     * 批量查询玩家资料。
     *
     * @param ids Steam ID64 列表
     * @return 与请求 ID 顺序一致的玩家资料列表
     */
    fun getProfiles(ids: ArrayList<Long>): List<PlayerProfile> {
        if (ids.isEmpty()) {
            return emptyList()
        }

        // 请求里可能有重复 ID，缓存和 Steam API 查询按去重后的集合执行，返回时再按原顺序组装。
        val uniqueIds = ids.distinct()
        val cachedProfiles = profileRepository.findAllById(uniqueIds).associateBy { it.steamID64 }
        val missingIds = uniqueIds.filterNot { cachedProfiles.containsKey(it) }

        // Redis 里没有的资料才访问 Steam，避免个人资料接口成为每次请求的外部依赖。
        val fetchedProfiles = if (missingIds.isEmpty()) {
            emptyMap()
        } else {
            fetchAndCacheProfiles(missingIds)
        }

        val profilesById = cachedProfiles + fetchedProfiles
        return ids.map { id ->
            // Steam 对部分 ID 可能不返回玩家资料，兜底值只保障接口契约，不写入缓存。
            profilesById[id] ?: fallbackEmptyProfile(id)
        }
    }

    /**
     * 查询单个玩家资料。
     *
     * @param id Steam ID64
     * @return 玩家资料
     */
    fun getOneProfile(id: Long): PlayerProfile {
        return getProfiles(arrayListOf(id)).first()
    }

    /**
     * fetchAndCacheProfiles
     * 从steam获取资料并缓存
     *
     * @param ids 要获取的id列表
     * @return 查询结果列表
     */
    private fun fetchAndCacheProfiles(ids: List<Long>): Map<Long, PlayerProfile> {
        val fetchedProfiles = steamApiUtils.getPlayerSummaries(ids)
            .response
            .players
            .map { PlayerProfile(
                steamID64 = it.steamId,
                name = it.personaName,
                // Steam 返回的有效玩家资料按接口契约一定包含非空 avatarFull，所以不需要对这个字段 fallback 默认值；没有资料时由 fallbackEmptyProfile 兜底。
                avatarLink = it.avatarFull
            ) }

        if (fetchedProfiles.isNotEmpty()) {
            profileRepository.saveAll(fetchedProfiles)
        }

        return fetchedProfiles.associateBy { it.steamID64 }

    }

    /**
     * fallbackEmptyProfile
     *  根据 steamid 返回默认资料
     *
     *  @param id SteamID
     */
    private fun fallbackEmptyProfile(id: SteamID64): PlayerProfile {
        val idText = id.toString()
        val suffix = idText.takeLast(6)
        return PlayerProfile(id, "用户$suffix", SteamUser.EMPTY_AVATAR)
    }
}

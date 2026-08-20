package me.ywj.cloudpvp.lobby.service

import me.ywj.cloudpvp.core.type.SteamID64
import me.ywj.cloudpvp.lobby.entity.Match
import me.ywj.cloudpvp.lobby.listener.MatchPlayerListener
import me.ywj.cloudpvp.lobby.repository.MatchRepository
import org.springframework.data.redis.listener.PatternTopic
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

/**
 * MatchSessionService
 * 管理比赛 WebSocket 会话与 Match Redis 频道监听器的绑定关系。
 *
 * @author sheip9
 * @since 2026/8/20 16:57
 */
@Service
class MatchSessionService(
    private val matchRepository: MatchRepository,
    val container: RedisMessageListenerContainer,
) {
    private val listenerList = ConcurrentHashMap<SteamID64, MatchPlayerListener>()

    /**
     * 校验玩家属于目标比赛后订阅对应 Match ID 频道。
     *
     * @param playerId 当前玩家 ID
     * @param matchId 目标比赛 ID
     * @param sendMatchFn 完整比赛快照发送函数
     * @return 成功订阅时返回 true，比赛不存在或玩家不属于比赛时返回 false
     */
    fun trySubscribe(
        playerId: SteamID64,
        matchId: String,
        sendMatchFn: (Match) -> Unit,
    ): Boolean {
        val match = matchRepository.findById(matchId).orElse(null) ?: return false
        val playerBelongsToMatch = match.teams.any { team ->
            team.members.any { member -> member.playerId == playerId.toString() }
        }
        if (!playerBelongsToMatch) {
            return false
        }

        val messageListener = MatchPlayerListener(matchId, matchRepository, sendMatchFn)
        container.addMessageListener(messageListener, PatternTopic(matchId))
        listenerList[playerId] = messageListener
        return true
    }

    /**
     * 移除当前玩家绑定的比赛监听器。
     *
     * @param playerId 当前玩家 ID
     */
    fun unsubscribe(playerId: SteamID64) {
        listenerList[playerId]?.let {
            container.removeMessageListener(it)
        }
        listenerList.remove(playerId)
    }
}

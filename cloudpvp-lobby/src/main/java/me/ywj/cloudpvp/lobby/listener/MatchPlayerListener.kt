package me.ywj.cloudpvp.lobby.listener

import me.ywj.cloudpvp.lobby.entity.Match
import me.ywj.cloudpvp.lobby.repository.MatchRepository
import org.springframework.data.redis.connection.Message
import org.springframework.data.redis.connection.MessageListener

/**
 * MatchPlayerListener
 * 收到比赛变更通知后查询并推送最新的完整比赛快照。
 *
 * @author sheip9
 * @since 2026/8/20 16:57
 */
class MatchPlayerListener(
    private val matchId: String,
    private val matchRepository: MatchRepository,
    private val matchSender: (Match) -> Unit,
) : MessageListener {
    /**
     * 将 Redis 事件作为变更通知，并以仓库中的最新比赛状态作为推送内容。
     *
     * @param message Redis 频道消息，消息体不承载比赛业务状态
     * @param pattern 命中的 Redis 订阅模式
     */
    override fun onMessage(message: Message, pattern: ByteArray?) {
        matchRepository.findById(matchId).ifPresent { match -> matchSender(match) }
    }
}

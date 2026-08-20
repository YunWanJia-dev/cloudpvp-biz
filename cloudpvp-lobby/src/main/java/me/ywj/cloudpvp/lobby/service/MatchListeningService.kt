package me.ywj.cloudpvp.lobby.service

import me.ywj.cloudpvp.lobby.model.messaging.MatchMessage
import me.ywj.cloudpvp.lobby.model.messaging.toEntity
import me.ywj.cloudpvp.lobby.repository.MatchRepository
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service

/**
 * MatchListeningService
 * 管理比赛的 MQ 回传
 *
 * @author sheip9
 * @since 2026/8/13 18:38
 */
@Service
class MatchListeningService(
    private val matchRepository: MatchRepository,
    private val redisTemplate: RedisTemplate<String, Any>,
) {
    @RabbitListener(queues = ["#{T(me.ywj.cloudpvp.lobby.constant.queue.MatchmakingQueue).MatchBiz.queueName}"])
    fun consumeMatch(message: MatchMessage) {
        val match = matchRepository.save(message.toEntity())
        // 频道消息只表示比赛已变化，WebSocket listener 会重新查询仓库并发送完整 Match。
        redisTemplate.convertAndSend(match.matchId, match.matchId)
    }
}

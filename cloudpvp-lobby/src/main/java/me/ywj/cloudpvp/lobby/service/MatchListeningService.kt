package me.ywj.cloudpvp.lobby.service

import me.ywj.cloudpvp.lobby.entity.Match
import me.ywj.cloudpvp.lobby.entity.MatchStatus
import me.ywj.cloudpvp.lobby.model.messaging.MatchMessage
import me.ywj.cloudpvp.lobby.model.messaging.toEntity
import me.ywj.cloudpvp.lobby.repository.MatchRepository
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.time.DateTimeException
import java.time.Instant

/**
 * MatchListeningService
 * 接收完整比赛生命周期消息，并将最新比赛快照保存和发布到 Redis。
 *
 * @author sheip9
 * @since 2026/8/14 17:49
 */
@Service
class MatchListeningService(
    private val matchRepository: MatchRepository,
    private val redisTemplate: RedisTemplate<String, Any>,
    private val lobbyListeningService: LobbyListeningService,
) {
    private val logger = LoggerFactory.getLogger(MatchListeningService::class.java)

    /**
     * 消费完整比赛生命周期消息，将合法的新快照保存后发布到对应 Match ID 频道。
     *
     * @param message Matcher 或 Allocator 发布的完整比赛消息
     */
    @RabbitListener(
        queues = ["#{T(me.ywj.cloudpvp.lobby.constant.queue.MatchmakingQueue).MatchBiz.queueName}"],
        exclusive = true,
        concurrency = "1",
    )
    fun consumeMatch(message: MatchMessage) {
        val match = message.toEntity()
        val lobbyIds = match.teams.flatMap { it.lobbyIds }.distinct()
        logger.info(
            "收到完整比赛消息: matchId={}, status={}, gameMode={}, teamCount={}, lobbyCount={}, " +
                "memberCount={}, serverIp={}",
            match.matchId,
            match.status,
            match.gameMode,
            match.teams.size,
            lobbyIds.size,
            match.teams.sumOf { it.members.size },
            match.server?.ip,
        )
        if (match.matchId.isBlank() || lobbyIds.isEmpty()) {
            logger.error(
                "忽略字段不完整的比赛消息: matchId={}, status={}, lobbyCount={}",
                match.matchId,
                match.status,
                lobbyIds.size,
            )
            return
        }
        if (match.status == MatchStatus.IN_PROGRESS && match.server?.ip.isNullOrBlank()) {
            logger.error(
                "忽略缺少有效服务器地址的 IN_PROGRESS 消息: matchId={}, server={}",
                match.matchId,
                match.server,
            )
            return
        }

        val incomingUpdatedAt = parseUpdatedAt(match) ?: return
        val existing = matchRepository.findById(match.matchId).orElse(null)
        if (existing != null && !canApplyMatch(existing, match, incomingUpdatedAt)) {
            return
        }

        if (existing != match) {
            matchRepository.save(match)
        }
        // 订阅后同步持久化快照，确保 RabbitMQ ACK 前大厅已经看到本次比赛状态。
        lobbyListeningService.ensureMatchSubscription(match.matchId)
        redisTemplate.convertAndSend(match.matchId, match)
        logger.info(
            "比赛快照已保存并发布: matchId={}, status={}, updatedAt={}",
            match.matchId,
            match.status,
            match.updatedAt,
        )
    }

    private fun canApplyMatch(existing: Match, incoming: Match, incomingUpdatedAt: Instant): Boolean {
        if (existing.status == MatchStatus.IN_PROGRESS && incoming.status == MatchStatus.WAITING_FOR_SERVER) {
            logger.warn(
                "忽略比赛生命周期倒退消息: matchId={}, currentStatus={}, incomingStatus={}, incomingUpdatedAt={}",
                incoming.matchId,
                existing.status,
                incoming.status,
                incoming.updatedAt,
            )
            return false
        }
        val existingUpdatedAt = parseStoredUpdatedAt(existing) ?: return true
        if (incomingUpdatedAt.isBefore(existingUpdatedAt)) {
            logger.warn(
                "忽略过期比赛消息: matchId={}, incomingStatus={}, incomingUpdatedAt={}, " +
                    "currentStatus={}, currentUpdatedAt={}",
                incoming.matchId,
                incoming.status,
                incoming.updatedAt,
                existing.status,
                existing.updatedAt,
            )
            return false
        }
        if (incomingUpdatedAt == existingUpdatedAt && incoming != existing) {
            logger.error(
                "忽略相同 updatedAt 的冲突比赛消息: matchId={}, incomingStatus={}, currentStatus={}, updatedAt={}",
                incoming.matchId,
                incoming.status,
                existing.status,
                incoming.updatedAt,
            )
            return false
        }
        return true
    }

    private fun parseUpdatedAt(match: Match): Instant? {
        return try {
            Instant.parse(match.updatedAt)
        } catch (exception: DateTimeException) {
            logger.error(
                "忽略 updated_at 非 RFC3339 时间的比赛消息: matchId={}, status={}, updatedAt={}",
                match.matchId,
                match.status,
                match.updatedAt,
                exception,
            )
            null
        }
    }

    private fun parseStoredUpdatedAt(match: Match): Instant? {
        return try {
            Instant.parse(match.updatedAt)
        } catch (exception: DateTimeException) {
            // 旧缓存无法参与版本比较时允许新消息修复它，避免坏数据永久阻塞比赛推进。
            logger.error(
                "已存比赛 updatedAt 无效，将使用新消息修复: matchId={}, status={}, updatedAt={}",
                match.matchId,
                match.status,
                match.updatedAt,
                exception,
            )
            null
        }
    }
}

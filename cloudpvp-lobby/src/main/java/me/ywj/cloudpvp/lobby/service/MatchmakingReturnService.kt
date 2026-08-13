package me.ywj.cloudpvp.lobby.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import me.ywj.cloudpvp.core.model.lobby.LobbyMessage
import me.ywj.cloudpvp.core.model.lobby.LobbyMessageType
import me.ywj.cloudpvp.core.model.lobby.LobbyStatus
import me.ywj.cloudpvp.lobby.entity.Lobby
import me.ywj.cloudpvp.lobby.model.MatchmakingLobbyStatus
import me.ywj.cloudpvp.lobby.model.MatchmakingLobbyStatusMessage
import me.ywj.cloudpvp.lobby.model.MatchmakingMatchMessage
import me.ywj.cloudpvp.lobby.model.MatchmakingMatchStatus
import me.ywj.cloudpvp.lobby.repository.LobbyRepository
import me.ywj.cloudpvp.lobby.utils.RedisLockUtils.withLobbyLock
import org.redisson.api.RedissonClient
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.time.DateTimeException
import java.time.Instant

/**
 * MatchmakingReturnService
 * 消费 Matcher 与 Allocator 回传并同步大厅的匹配生命周期。
 *
 * @author sheip9
 * @since 2026/8/13 16:27
 */
@Service
class MatchmakingReturnService(
    private val lobbyRepository: LobbyRepository,
    private val redisTemplate: RedisTemplate<String, Any>,
    private val redissonClient: RedissonClient,
) {
    private val logger = LoggerFactory.getLogger(MatchmakingReturnService::class.java)

    /**
     * 消费 Matcher 的单大厅状态更新并广播最新大厅快照。
     *
     * @param message 大厅状态更新消息
     */
    @RabbitListener(queues = ["#{T(me.ywj.cloudpvp.lobby.constant.queue.MatchmakingQueue).Lobby.queueName}"])
    fun consumeLobbyStatus(message: MatchmakingLobbyStatusMessage) = runBlocking {
        val lobbyId = message.lobbyId.toIntOrNull()
        if (lobbyId == null) {
            logger.error("忽略 Matcher 的无效大厅状态消息: lobbyId={}, status={}", message.lobbyId, message.status)
            return@runBlocking
        }
        logger.info(
            "收到 Matcher 大厅状态: lobbyId={}, status={}, reason={}",
            lobbyId,
            message.status,
            message.reason,
        )
        withLobbyLock(redissonClient, lobbyId) {
            val lobby = findLobby(lobbyId)
            if (lobby == null) {
                logger.warn("忽略不存在大厅的 Matcher 状态: lobbyId={}, status={}", lobbyId, message.status)
                return@withLobbyLock
            }
            val targetStatus = message.status.toLobbyStatus()
            if (!canApplyLobbyStatus(lobby.status, targetStatus)) {
                logger.warn(
                    "忽略过期的 Matcher 大厅状态: lobbyId={}, currentStatus={}, targetStatus={}",
                    lobbyId,
                    lobby.status,
                    targetStatus,
                )
                return@withLobbyLock
            }
            val stateChanged = lobby.status != targetStatus ||
                (targetStatus == LobbyStatus.WAITING && lobby.matchId != null)
            lobby.status = targetStatus
            if (targetStatus == LobbyStatus.WAITING) {
                // 单大厅状态事件没有时间戳，不能清掉完整 Match 游标，否则旧消息可能回退比赛状态。
                lobby.matchId = null
            }
            withContext(Dispatchers.IO) {
                if (stateChanged) lobbyRepository.save(lobby)
                publish(lobby, LobbyMessageType.LOBBY_SNAPSHOT, lobby)
            }
            logger.info(
                "Matcher 大厅状态已同步: lobbyId={}, status={}, stateChanged={}",
                lobbyId,
                lobby.status,
                stateChanged,
            )
        }
    }

    /**
     * 消费完整比赛生命周期消息；match.create 与 match.update 共用同一个 Biz 队列和模型。
     *
     * @param message 完整比赛消息
     */
    @RabbitListener(
        queues = ["#{T(me.ywj.cloudpvp.lobby.constant.queue.MatchmakingQueue).MatchBiz.queueName}"],
        exclusive = true,
        concurrency = "1",
    )
    fun consumeMatch(message: MatchmakingMatchMessage) = runBlocking {
        val updatedAt = parseUpdatedAt(message) ?: return@runBlocking
        val rawLobbyIds = message.teams.flatMap { it.lobbyIds }
        val distinctLobbyIds = rawLobbyIds.distinct()
        logger.info(
            "收到完整比赛消息: matchId={}, status={}, gameMode={}, teamCount={}, lobbyCount={}, " +
                "memberCount={}, serverIp={}, updatedAt={}",
            message.matchId,
            message.status,
            message.gameMode,
            message.teams.size,
            distinctLobbyIds.size,
            message.teams.sumOf { it.members.size },
            message.server?.ip,
            updatedAt,
        )
        if (message.matchId.isBlank() || distinctLobbyIds.isEmpty()) {
            logger.error(
                "忽略字段不完整的比赛消息: matchId={}, status={}, lobbyCount={}",
                message.matchId,
                message.status,
                distinctLobbyIds.size,
            )
            return@runBlocking
        }
        if (rawLobbyIds.size != distinctLobbyIds.size) {
            logger.warn(
                "比赛消息包含重复 lobbyId，将按大厅去重消费: matchId={}, rawCount={}, distinctCount={}",
                message.matchId,
                rawLobbyIds.size,
                distinctLobbyIds.size,
            )
        }

        for (rawLobbyId in distinctLobbyIds) {
            val lobbyId = rawLobbyId.toIntOrNull()
            if (lobbyId == null) {
                logger.error(
                    "忽略比赛消息中的无效 lobbyId: matchId={}, status={}, lobbyId={}",
                    message.matchId,
                    message.status,
                    rawLobbyId,
                )
                continue
            }
            withLobbyLock(redissonClient, lobbyId) {
                val lobby = findLobby(lobbyId)
                if (lobby == null) {
                    logger.warn(
                        "忽略不存在大厅的比赛消息: matchId={}, status={}, lobbyId={}",
                        message.matchId,
                        message.status,
                        lobbyId,
                    )
                    return@withLobbyLock
                }
                applyMatch(message, updatedAt, lobby)
            }
        }
        logger.info(
            "完整比赛消息消费结束: matchId={}, status={}, requestedLobbyCount={}",
            message.matchId,
            message.status,
            distinctLobbyIds.size,
        )
    }

    private suspend fun applyMatch(message: MatchmakingMatchMessage, updatedAt: Instant, lobby: Lobby) {
        val existingUpdatedAt = lobby.matchMessageUpdatedAt
        val advancesSameMatch = lobby.matchMessageId == message.matchId &&
            lobby.matchMessageStatus == MatchmakingMatchStatus.WAITING_FOR_SERVER &&
            message.status == MatchmakingMatchStatus.IN_PROGRESS
        // 跨服务时钟可能轻微漂移；同一比赛的生命周期前进优先于 updated_at 的墙钟顺序。
        if (!advancesSameMatch && existingUpdatedAt != null && updatedAt.isBefore(existingUpdatedAt)) {
            logger.warn(
                "忽略过期比赛消息: lobbyId={}, incomingMatchId={}, incomingStatus={}, incomingUpdatedAt={}, " +
                    "currentMatchId={}, currentStatus={}, currentUpdatedAt={}",
                lobby.id,
                message.matchId,
                message.status,
                updatedAt,
                lobby.matchMessageId,
                lobby.matchMessageStatus,
                existingUpdatedAt,
            )
            return
        }
        if (existingUpdatedAt != null && updatedAt == existingUpdatedAt &&
            (lobby.matchMessageId != message.matchId || lobby.matchMessageStatus != message.status)
        ) {
            logger.error(
                "忽略相同 updatedAt 的冲突比赛消息: lobbyId={}, incomingMatchId={}, incomingStatus={}, " +
                    "currentMatchId={}, currentStatus={}, updatedAt={}",
                lobby.id,
                message.matchId,
                message.status,
                lobby.matchMessageId,
                lobby.matchMessageStatus,
                updatedAt,
            )
            return
        }
        if (!canApplyMatchStatus(lobby, message)) {
            logger.warn(
                "忽略比赛生命周期倒退消息: lobbyId={}, matchId={}, currentStatus={}, incomingStatus={}, " +
                    "currentUpdatedAt={}, incomingUpdatedAt={}",
                lobby.id,
                message.matchId,
                lobby.matchMessageStatus,
                message.status,
                existingUpdatedAt,
                updatedAt,
            )
            return
        }

        when (message.status) {
            MatchmakingMatchStatus.WAITING_FOR_SERVER -> applyWaitingForServer(message, updatedAt, lobby)
            MatchmakingMatchStatus.IN_PROGRESS -> applyInProgress(message, updatedAt, lobby)
        }
    }

    private suspend fun applyWaitingForServer(message: MatchmakingMatchMessage, updatedAt: Instant, lobby: Lobby) {
        if (message.server != null) {
            logger.warn(
                "WAITING_FOR_SERVER 比赛意外携带 server，Biz 仍按等待服务器处理: matchId={}, lobbyId={}, serverIp={}",
                message.matchId,
                lobby.id,
                message.server.ip,
            )
        }
        if (lobby.matchId != null && lobby.matchId != message.matchId) {
            logger.error(
                "忽略与现有比赛冲突的 WAITING_FOR_SERVER 消息: lobbyId={}, currentMatchId={}, incomingMatchId={}",
                lobby.id,
                lobby.matchId,
                message.matchId,
            )
            return
        }
        if (lobby.status != LobbyStatus.MATCHING && lobby.status != LobbyStatus.MATCHED) {
            logger.warn(
                "忽略当前大厅状态不允许的 WAITING_FOR_SERVER 消息: lobbyId={}, matchId={}, currentStatus={}",
                lobby.id,
                message.matchId,
                lobby.status,
            )
            return
        }

        val stateChanged = lobby.status != LobbyStatus.MATCHED ||
            lobby.matchId != message.matchId || !sameCursor(lobby, message, updatedAt)
        lobby.status = LobbyStatus.MATCHED
        lobby.matchId = message.matchId
        updateCursor(lobby, message, updatedAt)
        persistThenPublishIfNeeded(lobby, stateChanged, LobbyMessageType.MATCH_SUCCESS, message)
        logger.info(
            "等待服务器的比赛已同步到大厅: matchId={}, lobbyId={}, stateChanged={}, updatedAt={}",
            message.matchId,
            lobby.id,
            stateChanged,
            updatedAt,
        )
    }

    private suspend fun applyInProgress(message: MatchmakingMatchMessage, updatedAt: Instant, lobby: Lobby) {
        val server = message.server
        if (server == null || server.ip.isBlank()) {
            logger.error(
                "忽略缺少有效服务器地址的 IN_PROGRESS 消息: matchId={}, lobbyId={}, server={}",
                message.matchId,
                lobby.id,
                server,
            )
            return
        }
        if (lobby.matchId != message.matchId) {
            logger.warn(
                "忽略无法关联到大厅当前比赛的 IN_PROGRESS 消息: lobbyId={}, currentMatchId={}, incomingMatchId={}",
                lobby.id,
                lobby.matchId,
                message.matchId,
            )
            return
        }
        if (lobby.status != LobbyStatus.MATCHED) {
            logger.warn(
                "忽略当前大厅状态不允许的 IN_PROGRESS 消息: lobbyId={}, matchId={}, currentStatus={}",
                lobby.id,
                message.matchId,
                lobby.status,
            )
            return
        }

        val stateChanged = !sameCursor(lobby, message, updatedAt)
        updateCursor(lobby, message, updatedAt)
        // 服务器就绪不等价于玩家已经进入游戏；Lobby 保持 MATCHED，交由后续玩家确认流程推进。
        persistThenPublishIfNeeded(lobby, stateChanged, LobbyMessageType.GAME_CONFIRMED, message)
        logger.info(
            "比赛服务器已同步到大厅: matchId={}, lobbyId={}, serverIp={}, " +
                "stateChanged={}, updatedAt={}",
            message.matchId,
            lobby.id,
            server.ip,
            stateChanged,
            updatedAt,
        )
    }

    private suspend fun persistThenPublishIfNeeded(
        lobby: Lobby,
        stateChanged: Boolean,
        type: LobbyMessageType,
        message: MatchmakingMatchMessage,
    ) = withContext(Dispatchers.IO) {
        if (stateChanged) lobbyRepository.save(lobby)
        // 同一状态重投不重复保存，但仍重播事件，覆盖 save 成功而 Redis 发布失败的场景。
        publish(lobby, type, message)
    }

    private fun parseUpdatedAt(message: MatchmakingMatchMessage): Instant? {
        return try {
            Instant.parse(message.updatedAt)
        } catch (exception: DateTimeException) {
            logger.error(
                "忽略 updated_at 非 RFC3339 时间的比赛消息: matchId={}, status={}, updatedAt={}",
                message.matchId,
                message.status,
                message.updatedAt,
                exception,
            )
            null
        }
    }

    private suspend fun findLobby(lobbyId: Int): Lobby? = withContext(Dispatchers.IO) {
        lobbyRepository.findById(lobbyId).orElse(null)
    }

    private fun publish(lobby: Lobby, type: LobbyMessageType, data: Any) {
        redisTemplate.convertAndSend(
            lobby.id.toString(),
            LobbyMessage(type).apply { this.data = data },
        )
    }

    private fun sameCursor(lobby: Lobby, message: MatchmakingMatchMessage, updatedAt: Instant): Boolean {
        return lobby.matchMessageId == message.matchId &&
            lobby.matchMessageStatus == message.status &&
            lobby.matchMessageUpdatedAt == updatedAt
    }

    /**
     * 限制同一比赛只能向前推进；IN_PROGRESS 不能被迟到的 WAITING_FOR_SERVER 覆盖。
     */
    private fun canApplyMatchStatus(lobby: Lobby, message: MatchmakingMatchMessage): Boolean {
        if (lobby.matchMessageId != message.matchId) return true
        return when (lobby.matchMessageStatus) {
            null -> true
            MatchmakingMatchStatus.WAITING_FOR_SERVER -> true
            MatchmakingMatchStatus.IN_PROGRESS -> message.status == MatchmakingMatchStatus.IN_PROGRESS
        }
    }

    private fun updateCursor(lobby: Lobby, message: MatchmakingMatchMessage, updatedAt: Instant) {
        lobby.matchMessageId = message.matchId
        lobby.matchMessageStatus = message.status
        lobby.matchMessageUpdatedAt = updatedAt
    }

    private fun MatchmakingLobbyStatus.toLobbyStatus(): LobbyStatus {
        return when (this) {
            MatchmakingLobbyStatus.WAITING -> LobbyStatus.WAITING
            MatchmakingLobbyStatus.MATCHING -> LobbyStatus.MATCHING
            MatchmakingLobbyStatus.MATCHED -> LobbyStatus.MATCHED
        }
    }

    private fun canApplyLobbyStatus(currentStatus: LobbyStatus, targetStatus: LobbyStatus): Boolean {
        return when (targetStatus) {
            LobbyStatus.MATCHING -> currentStatus == LobbyStatus.MATCHING
            LobbyStatus.WAITING -> currentStatus == LobbyStatus.MATCHING || currentStatus == LobbyStatus.WAITING
            LobbyStatus.MATCHED -> currentStatus == LobbyStatus.MATCHING || currentStatus == LobbyStatus.MATCHED
            LobbyStatus.IN_GAME -> currentStatus == LobbyStatus.MATCHED || currentStatus == LobbyStatus.IN_GAME
        }
    }
}

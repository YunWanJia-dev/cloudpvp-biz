package me.ywj.cloudpvp.lobby.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import me.ywj.cloudpvp.core.model.lobby.LobbyMessage
import me.ywj.cloudpvp.core.model.lobby.LobbyMessageType
import me.ywj.cloudpvp.core.model.lobby.LobbyStatus
import me.ywj.cloudpvp.lobby.entity.Lobby
import me.ywj.cloudpvp.lobby.model.messaging.LobbyUpdateMessage
import me.ywj.cloudpvp.lobby.model.messaging.MatchMessage
import me.ywj.cloudpvp.lobby.model.messaging.MatchmakingMatchStatus
import me.ywj.cloudpvp.lobby.repository.LobbyRepository
import me.ywj.cloudpvp.lobby.utils.RedisLockUtils.withLobbyLock
import org.redisson.api.RedissonClient
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service

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
    fun consumeLobbyStatus(message: LobbyUpdateMessage) = runBlocking {
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
            val targetStatus = message.status
            if (!canApplyLobbyStatus(lobby, targetStatus)) {
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
    fun consumeMatch(message: MatchMessage) = runBlocking {
        val rawLobbyIds = message.teams.flatMap { it.lobbyIds }
        val distinctLobbyIds = rawLobbyIds.distinct()
        logger.info(
            "收到完整比赛消息: matchId={}, status={}, gameMode={}, teamCount={}, lobbyCount={}, " +
            "memberCount={}, serverIp={}",
            message.matchId,
            message.status,
            message.gameMode,
            message.teams.size,
            distinctLobbyIds.size,
            message.teams.sumOf { it.members.size },
            message.server?.ip,
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
                applyMatch(message, lobby)
            }
        }
        logger.info(
            "完整比赛消息消费结束: matchId={}, status={}, requestedLobbyCount={}",
            message.matchId,
            message.status,
            distinctLobbyIds.size,
        )
    }

    private suspend fun applyMatch(message: MatchMessage, lobby: Lobby) {
        when (message.status) {
            MatchmakingMatchStatus.WAITING_FOR_SERVER -> applyWaitingForServer(message, lobby)
            MatchmakingMatchStatus.IN_PROGRESS -> applyInProgress(message, lobby)
        }
    }

    private suspend fun applyWaitingForServer(message: MatchMessage, lobby: Lobby) {
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
        if (lobby.status != LobbyStatus.MATCHING) {
            logger.warn(
                "忽略当前大厅状态不允许的 WAITING_FOR_SERVER 消息: lobbyId={}, matchId={}, currentStatus={}",
                lobby.id,
                message.matchId,
                lobby.status,
            )
            return
        }

        val stateChanged = lobby.matchId != message.matchId
        lobby.matchId = message.matchId
        persistThenPublishIfNeeded(lobby, stateChanged, LobbyMessageType.MATCH_SUCCESS, message)
        logger.info(
            "等待服务器的比赛已同步到大厅: matchId={}, lobbyId={}, stateChanged={}",
            message.matchId,
            lobby.id,
            stateChanged,
        )
    }

    private suspend fun applyInProgress(message: MatchMessage, lobby: Lobby) {
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
        if (lobby.status != LobbyStatus.MATCHING && lobby.status != LobbyStatus.IN_MATCH) {
            logger.warn(
                "忽略当前大厅状态不允许的 IN_PROGRESS 消息: lobbyId={}, matchId={}, currentStatus={}",
                lobby.id,
                message.matchId,
                lobby.status,
            )
            return
        }

        val stateChanged = lobby.status != LobbyStatus.IN_MATCH
        lobby.status = LobbyStatus.IN_MATCH
        persistThenPublishIfNeeded(lobby, stateChanged, LobbyMessageType.GAME_CONFIRMED, message)
        logger.info(
            "比赛服务器已同步到大厅: matchId={}, lobbyId={}, serverIp={}, stateChanged={}",
            message.matchId,
            lobby.id,
            server.ip,
            stateChanged,
        )
    }

    private suspend fun persistThenPublishIfNeeded(
        lobby: Lobby,
        stateChanged: Boolean,
        type: LobbyMessageType,
        message: MatchMessage,
    ) = withContext(Dispatchers.IO) {
        if (stateChanged) lobbyRepository.save(lobby)
        // 同一状态重投不重复保存，但仍重播事件，覆盖 save 成功而 Redis 发布失败的场景。
        publish(lobby, type, message)
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

    private fun canApplyLobbyStatus(lobby: Lobby, targetStatus: LobbyStatus): Boolean {
        return when (targetStatus) {
            // 完整 Match 消息已经关联比赛后，迟到的单大厅状态不得覆盖比赛生命周期。
            LobbyStatus.MATCHING -> lobby.status == LobbyStatus.MATCHING && lobby.matchId == null
            LobbyStatus.WAITING ->
                (lobby.status == LobbyStatus.MATCHING || lobby.status == LobbyStatus.WAITING) && lobby.matchId == null
            LobbyStatus.IN_MATCH -> false
        }
    }
}

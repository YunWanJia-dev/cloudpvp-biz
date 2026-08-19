package me.ywj.cloudpvp.lobby.service

import jakarta.annotation.PreDestroy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import me.ywj.cloudpvp.lobby.model.publishing.LobbyMessage
import me.ywj.cloudpvp.lobby.model.publishing.LobbyMessageType
import me.ywj.cloudpvp.core.model.lobby.LobbyStatus
import me.ywj.cloudpvp.lobby.entity.Lobby
import me.ywj.cloudpvp.lobby.entity.Match
import me.ywj.cloudpvp.lobby.entity.MatchStatus
import me.ywj.cloudpvp.lobby.model.messaging.LobbyUpdateMessage
import me.ywj.cloudpvp.lobby.repository.LobbyRepository
import me.ywj.cloudpvp.lobby.repository.MatchRepository
import me.ywj.cloudpvp.lobby.utils.RedisLockUtils.withLobbyLock
import org.redisson.api.RedissonClient
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.data.redis.connection.MessageListener
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

/**
 * LobbyListeningService
 * 监听 Matcher 大厅状态以及按 Match ID 分发的 Redis 比赛更新，并同步关联大厅。
 *
 * @author sheip9
 * @since 2026/8/14 17:49
 */
@Service
class LobbyListeningService(
    private val lobbyRepository: LobbyRepository,
    private val matchRepository: MatchRepository,
    private val redisTemplate: RedisTemplate<String, Any>,
    private val redissonClient: RedissonClient,
    private val container: RedisMessageListenerContainer,
) {
    private val logger = LoggerFactory.getLogger(LobbyListeningService::class.java)
    private val matchSubscriptions = ConcurrentHashMap<String, MatchSubscription>()
    private val matchSubscriptionMonitor = Any()

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
     * 确保当前进程已订阅指定 Match ID，并同步一次 Redis 中的持久化比赛快照。
     *
     * @param matchId 需要监听的比赛唯一标识
     */
    fun ensureMatchSubscription(matchId: String) {
        require(matchId.isNotBlank()) { "matchId must not be blank" }
        val subscription = synchronized(matchSubscriptionMonitor) {
            matchSubscriptions[matchId] ?: MatchSubscription(matchId).also { newSubscription ->
                // 精确频道不会把 Match ID 中的模式字符解释成 Redis glob。
                try {
                    container.addMessageListener(newSubscription, matchTopic(matchId))
                } catch (exception: Throwable) {
                    // Spring 容器可能已写入内部映射后才在 SUBSCRIBE 阶段失败，必须补偿移除半注册监听器。
                    runCatching {
                        container.removeMessageListener(newSubscription, matchTopic(matchId))
                    }.onFailure { cleanupFailure ->
                        if (cleanupFailure !== exception) exception.addSuppressed(cleanupFailure)
                    }
                    throw exception
                }
                matchSubscriptions[matchId] = newSubscription
                logger.info("已订阅比赛 Redis 频道: matchId={}", matchId)
            }
        }

        try {
            // Pub/Sub 不保留历史消息；注册后回放持久化快照，封住保存与订阅之间的丢消息窗口。
            subscription.synchronizeCurrentSnapshot()
        } catch (exception: Throwable) {
            removeMatchSubscription(subscription)
            throw exception
        }
    }

    /**
     * Bean 销毁时移除所有动态比赛订阅。
     */
    @PreDestroy
    fun destroy() {
        val subscriptions = synchronized(matchSubscriptionMonitor) {
            matchSubscriptions.values.toList().also { matchSubscriptions.clear() }
        }
        subscriptions.forEach { subscription ->
            container.removeMessageListener(subscription, matchTopic(subscription.matchId))
        }
    }

    private suspend fun applyMatch(match: Match) {
        val rawLobbyIds = match.teams.flatMap { it.lobbyIds }
        val distinctLobbyIds = rawLobbyIds.distinct()
        if (match.matchId.isBlank() || distinctLobbyIds.isEmpty()) {
            logger.error(
                "忽略字段不完整的 Redis 比赛快照: matchId={}, status={}, lobbyCount={}",
                match.matchId,
                match.status,
                distinctLobbyIds.size,
            )
            return
        }
        if (rawLobbyIds.size != distinctLobbyIds.size) {
            logger.warn(
                "比赛快照包含重复 lobbyId，将按大厅去重同步: matchId={}, rawCount={}, distinctCount={}",
                match.matchId,
                rawLobbyIds.size,
                distinctLobbyIds.size,
            )
        }

        for (rawLobbyId in distinctLobbyIds) {
            val lobbyId = rawLobbyId.toIntOrNull()
            if (lobbyId == null) {
                logger.error(
                    "忽略比赛快照中的无效 lobbyId: matchId={}, status={}, lobbyId={}",
                    match.matchId,
                    match.status,
                    rawLobbyId,
                )
                continue
            }
            withLobbyLock(redissonClient, lobbyId) {
                val lobby = findLobby(lobbyId)
                if (lobby == null) {
                    logger.warn(
                        "忽略不存在大厅的比赛快照: matchId={}, status={}, lobbyId={}",
                        match.matchId,
                        match.status,
                        lobbyId,
                    )
                    return@withLobbyLock
                }
                when (match.status) {
                    MatchStatus.WAITING_FOR_SERVER -> applyWaitingForServer(match, lobby)
                    MatchStatus.IN_PROGRESS -> applyInProgress(match, lobby)
                }
            }
        }
    }

    private suspend fun applyWaitingForServer(match: Match, lobby: Lobby) {
        if (match.server != null) {
            logger.warn(
                "WAITING_FOR_SERVER 比赛意外携带 server，Lobby 仍按等待服务器处理: " +
                    "matchId={}, lobbyId={}, serverIp={}",
                match.matchId,
                lobby.id,
                match.server.ip,
            )
        }
        if (lobby.matchId != null && lobby.matchId != match.matchId) {
            logger.error(
                "忽略与现有比赛冲突的 WAITING_FOR_SERVER 快照: " +
                    "lobbyId={}, currentMatchId={}, incomingMatchId={}",
                lobby.id,
                lobby.matchId,
                match.matchId,
            )
            return
        }
        if (lobby.status != LobbyStatus.MATCHING) {
            logger.warn(
                "忽略当前大厅状态不允许的 WAITING_FOR_SERVER 快照: lobbyId={}, matchId={}, currentStatus={}",
                lobby.id,
                match.matchId,
                lobby.status,
            )
            return
        }

        val stateChanged = lobby.matchId != match.matchId
        lobby.matchId = match.matchId
        persistThenPublish(lobby, stateChanged, LobbyMessageType.MATCH_SUCCESS, match)
        logger.info(
            "等待服务器的比赛已同步到大厅: matchId={}, lobbyId={}, stateChanged={}",
            match.matchId,
            lobby.id,
            stateChanged,
        )
    }

    private suspend fun applyInProgress(match: Match, lobby: Lobby) {
        val server = match.server
        if (server == null || server.ip.isBlank()) {
            logger.error(
                "忽略缺少有效服务器地址的 IN_PROGRESS 快照: matchId={}, lobbyId={}, server={}",
                match.matchId,
                lobby.id,
                server,
            )
            return
        }
        if (lobby.matchId != match.matchId) {
            logger.warn(
                "忽略无法关联到大厅当前比赛的 IN_PROGRESS 快照: " +
                    "lobbyId={}, currentMatchId={}, incomingMatchId={}",
                lobby.id,
                lobby.matchId,
                match.matchId,
            )
            return
        }
        if (lobby.status != LobbyStatus.MATCHING && lobby.status != LobbyStatus.IN_MATCH) {
            logger.warn(
                "忽略当前大厅状态不允许的 IN_PROGRESS 快照: lobbyId={}, matchId={}, currentStatus={}",
                lobby.id,
                match.matchId,
                lobby.status,
            )
            return
        }

        val stateChanged = lobby.status != LobbyStatus.IN_MATCH
        lobby.status = LobbyStatus.IN_MATCH
        persistThenPublish(lobby, stateChanged, LobbyMessageType.GAME_CONFIRMED, match)
        logger.info(
            "比赛服务器已同步到大厅: matchId={}, lobbyId={}, serverIp={}, stateChanged={}",
            match.matchId,
            lobby.id,
            server.ip,
            stateChanged,
        )
    }

    private suspend fun persistThenPublish(
        lobby: Lobby,
        stateChanged: Boolean,
        type: LobbyMessageType,
        match: Match,
    ) = withContext(Dispatchers.IO) {
        if (stateChanged) lobbyRepository.save(lobby)
        // 重投时仍广播客户端事件，以覆盖大厅保存成功但上次频道发布失败的场景。
        publish(lobby, type, match)
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

    private fun removeMatchSubscription(subscription: MatchSubscription) {
        val removed = synchronized(matchSubscriptionMonitor) {
            matchSubscriptions.remove(subscription.matchId, subscription)
        }
        if (removed) {
            container.removeMessageListener(subscription, matchTopic(subscription.matchId))
            logger.info("已取消比赛 Redis 频道订阅: matchId={}", subscription.matchId)
        }
    }

    private fun matchTopic(matchId: String): ChannelTopic = ChannelTopic(matchId)

    private fun canApplyLobbyStatus(lobby: Lobby, targetStatus: LobbyStatus): Boolean {
        return when (targetStatus) {
            // 完整 Match 快照已经关联比赛后，迟到的单大厅状态不得覆盖比赛生命周期。
            LobbyStatus.MATCHING -> lobby.status == LobbyStatus.MATCHING && lobby.matchId == null
            LobbyStatus.WAITING ->
                (lobby.status == LobbyStatus.MATCHING || lobby.status == LobbyStatus.WAITING) && lobby.matchId == null
            LobbyStatus.IN_MATCH -> false
        }
    }

    private inner class MatchSubscription(val matchId: String) : MessageListener {
        private var publishedSnapshotToSkip: MatchVersion? = null

        override fun onMessage(message: org.springframework.data.redis.connection.Message, pattern: ByteArray?) {
            try {
                // 频道消息只承担变更通知；业务状态始终从 MatchRepository 读取，避免依赖 Pub/Sub 反序列化类型信息。
                val completed = synchronized(this) {
                    val match = findCurrentMatch() ?: return@synchronized false
                    val version = MatchVersion(match.status, match.updatedAt)
                    if (publishedSnapshotToSkip == version) {
                        // ensureMatchSubscription 已同步过同一快照，首次 Pub/Sub 通知只用于确认发布链路。
                        publishedSnapshotToSkip = null
                    } else {
                        publishedSnapshotToSkip = null
                        runBlocking { applyMatch(match) }
                    }
                    match.status == MatchStatus.IN_PROGRESS
                }
                if (completed) {
                    // 当前协议仅有 IN_PROGRESS 终态；WAITING_FOR_SERVER 必须持续监听，直到上游补充失败/取消状态。
                    removeMatchSubscription(this)
                }
            } catch (exception: Throwable) {
                logger.error("同步 Redis 比赛通知失败: matchId={}", matchId, exception)
            }
        }

        /**
         * 回放 Redis 中当前比赛快照，并记录下一条同版本发布通知以避免重复广播。
         */
        fun synchronizeCurrentSnapshot() {
            synchronized(this) {
                val match = findCurrentMatch()
                    ?: throw IllegalStateException("Match $matchId was not found after it was saved")
                runBlocking { applyMatch(match) }
                publishedSnapshotToSkip = MatchVersion(match.status, match.updatedAt)
            }
        }

        private fun findCurrentMatch(): Match? = matchRepository.findById(matchId).orElse(null)
    }

    private data class MatchVersion(
        val status: MatchStatus,
        val updatedAt: String,
    )
}

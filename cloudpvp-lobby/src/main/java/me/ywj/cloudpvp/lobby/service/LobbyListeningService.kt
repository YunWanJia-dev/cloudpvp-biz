package me.ywj.cloudpvp.lobby.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import me.ywj.cloudpvp.core.model.lobby.LobbyMessage
import me.ywj.cloudpvp.core.model.lobby.LobbyMessageType
import me.ywj.cloudpvp.core.model.lobby.LobbyStatus
import me.ywj.cloudpvp.lobby.entity.Lobby
import me.ywj.cloudpvp.lobby.model.messaging.LobbyUpdateMessage
import me.ywj.cloudpvp.lobby.repository.LobbyRepository
import me.ywj.cloudpvp.lobby.utils.RedisLockUtils.withLobbyLock
import org.redisson.api.RedissonClient
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service

/**
 * LobbyListeningService
 * 监听 Matcher 回传的大厅状态更新并同步大厅。
 *
 * @author sheip9
 * @since 2026/8/14 17:49
 */
@Service
class LobbyListeningService(
    private val lobbyRepository: LobbyRepository,
    private val redisTemplate: RedisTemplate<String, Any>,
    private val redissonClient: RedissonClient,
) {
    private val logger = LoggerFactory.getLogger(LobbyListeningService::class.java)

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
                publish(lobby)
            }
            logger.info(
                "Matcher 大厅状态已同步: lobbyId={}, status={}, stateChanged={}",
                lobbyId,
                lobby.status,
                stateChanged,
            )
        }
    }

    private suspend fun findLobby(lobbyId: Int): Lobby? = withContext(Dispatchers.IO) {
        lobbyRepository.findById(lobbyId).orElse(null)
    }

    private fun publish(lobby: Lobby) {
        redisTemplate.convertAndSend(
            lobby.id.toString(),
            LobbyMessage(LobbyMessageType.LOBBY_SNAPSHOT).apply { data = lobby },
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

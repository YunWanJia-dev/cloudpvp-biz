package me.ywj.cloudpvp.lobby.service

import me.ywj.cloudpvp.lobby.model.messaging.LobbyUpdateMessage
import me.ywj.cloudpvp.lobby.model.publishing.LobbyMessage
import me.ywj.cloudpvp.lobby.model.publishing.LobbyMessageType
import me.ywj.cloudpvp.lobby.repository.LobbyRepository
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service

/**
 * LobbyListeningService
 * 管理大厅的 MQ 回传
 *
 * @author sheip9
 * @since 2026/8/14 17:54
 */
@Service
class LobbyListeningService(
    private val lobbyRepository: LobbyRepository,
    private val redisTemplate: RedisTemplate<String, Any>,
) {
    @RabbitListener(queues = ["#{T(me.ywj.cloudpvp.lobby.constant.queue.MatchmakingQueue).Lobby.queueName}"])
    fun consumeLobbyStatus(message: LobbyUpdateMessage) {
        val lobbyOption = lobbyRepository.findById(message.lobbyId.toInt())
        if (!lobbyOption.isPresent) {
            // TODO: 潜在可能的匹配的同时取消匹配了
            return
        }
        val lobby = lobbyOption.get()
        lobby.apply {
            status = message.status
            matchId = message.matchId
        }
        lobbyRepository.save(lobby)
        redisTemplate.convertAndSend(
            lobby.id.toString(),
            LobbyMessage(LobbyMessageType.SHOULD_SYNC, null, ""),
        )
    }
}

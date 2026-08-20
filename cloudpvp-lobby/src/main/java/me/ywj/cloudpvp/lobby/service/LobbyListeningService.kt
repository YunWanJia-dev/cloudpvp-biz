package me.ywj.cloudpvp.lobby.service

import me.ywj.cloudpvp.lobby.model.messaging.LobbyUpdateMessage
import me.ywj.cloudpvp.lobby.model.publishing.LobbyMessage
import me.ywj.cloudpvp.lobby.model.publishing.LobbyMessageType
import me.ywj.cloudpvp.lobby.repository.LobbyRepository
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service

@Service
class LobbyListeningService(
    private val lobbyRepository: LobbyRepository,
    private val redisTemplate: RedisTemplate<String, Any>,
) {
    @RabbitListener(queues = ["#{T(me.ywj.cloudpvp.lobby.constant.queue.MatchmakingQueue).Lobby.queueName}"])
    fun consumeLobbyStatus(message: LobbyUpdateMessage) {
        val lobby = lobbyRepository.findById(message.lobbyId.toInt()).orElseThrow()
        lobby.status = message.status
        lobbyRepository.save(lobby)
        redisTemplate.convertAndSend(
            lobby.id.toString(),
            LobbyMessage(LobbyMessageType.SHOULD_SYNC, null, ""),
        )
    }
}

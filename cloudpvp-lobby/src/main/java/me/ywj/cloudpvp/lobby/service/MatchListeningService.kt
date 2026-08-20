package me.ywj.cloudpvp.lobby.service

import me.ywj.cloudpvp.lobby.model.messaging.MatchMessage
import me.ywj.cloudpvp.lobby.model.messaging.toEntity
import me.ywj.cloudpvp.lobby.repository.MatchRepository
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Service

@Service
class MatchListeningService(
    private val matchRepository: MatchRepository,
) {
    @RabbitListener(queues = ["#{T(me.ywj.cloudpvp.lobby.constant.queue.MatchmakingQueue).MatchBiz.queueName}"])
    fun consumeMatch(message: MatchMessage) {
        matchRepository.save(message.toEntity())
    }
}

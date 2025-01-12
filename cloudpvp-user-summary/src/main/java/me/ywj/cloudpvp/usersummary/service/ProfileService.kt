package me.ywj.cloudpvp.usersummary.service

import me.ywj.cloudpvp.core.constant.mq.Topic
import me.ywj.cloudpvp.core.constant.steam.SteamUser
import me.ywj.cloudpvp.core.entity.PlayerProfile
import org.apache.rocketmq.client.producer.SendCallback
import org.apache.rocketmq.client.producer.SendResult
import org.apache.rocketmq.spring.core.RocketMQTemplate
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.messaging.support.MessageBuilder
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux

/**
 * ProfileService
 *
 * @author sheip9
 * @since 2024/10/20 18:30
 */
@Service
class ProfileService @Autowired constructor(val rocketMQTemplate: RocketMQTemplate) {
    val mqCallback = MQSendCallback()

    fun getProfile(ids: ArrayList<Long>): List<PlayerProfile?>? {
        //先暂时这样 嘻嘻
        //TODO: 完成玩家数据从数据库查询
        return Flux.fromIterable(ids).map { id ->
            if (id == 76561198842604564) {
                return@map PlayerProfile(
                    id,
                    "sheip9",
                    "https://avatars.cdn.steamchina.eccdnx.com/2c78662993345cc1662988633b4e4198b6d1c7a5.jpg"
                )
            }
            PlayerProfile(
                id,
                "用户${id.toString().substring(id.toString().length - 6, id.toString().length)}",
                SteamUser.EMPTY_AVATAR
            )
        }.collectList().block()
    }

    fun requestUpdateProfile(id: Long) {
        MessageBuilder.withPayload<Long>(id)
        rocketMQTemplate.asyncSend(
            Topic.UPDATE_PLAYER_PROFILE, MessageBuilder.withPayload<Long>(id).build(),
            mqCallback
        )
    }
}

class MQSendCallback : SendCallback {
    override fun onSuccess(p0: SendResult?) {
    }

    override fun onException(p0: Throwable?) {
        p0?.printStackTrace()
    }

}

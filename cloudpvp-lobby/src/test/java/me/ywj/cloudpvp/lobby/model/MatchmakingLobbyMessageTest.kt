package me.ywj.cloudpvp.lobby.model

import me.ywj.cloudpvp.core.utils.JacksonUtils
import me.ywj.cloudpvp.lobby.configurations.RabbitMQConfiguration
import me.ywj.cloudpvp.lobby.entity.Lobby
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.amqp.core.MessageProperties

/**
 * MatchmakingLobbyMessageTest
 * 校验 lobby 到 matcher 的 RabbitMQ 消息契约。
 *
 * @author sheip9
 * @since 2026/8/12 14:31
 */
class MatchmakingLobbyMessageTest {
    private val objectMapper = JacksonUtils.INSTANCE

    /**
     * 验证业务大厅会转换成 matcher 所需的字段名和字段类型。
     */
    @Test
    fun fromLobbyUsesMatcherContract() {
        val lobby = Lobby(123, arrayListOf(76561198000000001L, 76561198000000002L))

        val message = MatchmakingLobbyMessage.from(lobby)
        val json = objectMapper.readTree(objectMapper.writeValueAsBytes(message))

        assertThat(json["lobby_id"].asText()).isEqualTo("123")
        assertThat(json["game_mode"].asText()).isEqualTo("matchmaker/5v5/competitive")
        assertThat(json["members"].map { it["player_id"].asText() })
            .containsExactly("76561198000000001", "76561198000000002")
        assertThat(json.has("id")).isFalse()
        assertThat(json.has("players")).isFalse()
    }

    /**
     * 验证 Spring AMQP 转换器会产生 matcher 可消费的 JSON 消息体。
     */
    @Test
    fun rabbitConverterWritesJsonBody() {
        val converter = RabbitMQConfiguration().rabbitMessageConverter(objectMapper)
        val message = converter.toMessage(
            MatchmakingLobbyMessage.from(Lobby(456, arrayListOf(76561198000000003L))),
            MessageProperties(),
        )
        val json = objectMapper.readTree(message.body)

        assertThat(message.messageProperties.contentType).isEqualTo("application/json")
        assertThat(json["lobby_id"].asText()).isEqualTo("456")
        assertThat(json["members"][0]["player_id"].asText()).isEqualTo("76561198000000003")
    }
}

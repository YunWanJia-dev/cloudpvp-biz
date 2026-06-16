package me.ywj.cloudpvp.lobby.configurations;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.ywj.cloudpvp.lobby.constant.queue.MatchmakingQueue;
import me.ywj.cloudpvp.lobby.constant.routingkey.MatchmakingKey;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQConfiguration
 * RabbitMQ 队列与交换机配置。
 *
 * @author sheip9
 * @since 2026/6/16 13:33
 */
@Configuration
public class RabbitMQConfiguration {
    public static final String MATCHMAKING_EXCHANGE_NAME = "cloudpvp.matchmaking.exchange";

    /**
     * 声明匹配系统使用的主题交换机。
     *
     * @return 持久化的匹配系统交换机
     */
    @Bean
    public TopicExchange matchmakingExchange() {
        return ExchangeBuilder.topicExchange(MATCHMAKING_EXCHANGE_NAME).durable(true).build();
    }

    /**
     * 使用项目统一的 ObjectMapper 序列化 RabbitMQ 消息。
     *
     * @param objectMapper 项目统一的 JSON 序列化器
     * @return RabbitMQ JSON 消息转换器
     */
    @Bean
    public MessageConverter rabbitMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    /**
     * 声明接收匹配提交消息的队列。
     *
     * @return 匹配提交队列
     */
    @Bean
    public Queue matchmakingSubmitQueue() {
        return QueueBuilder.durable(MatchmakingQueue.Submit.queueName).build();
    }

    /**
     * 声明接收匹配取消消息的队列。
     *
     * @return 匹配取消队列
     */
    @Bean
    public Queue matchmakingCancelQueue() {
        return QueueBuilder.durable(MatchmakingQueue.Cancel.queueName).build();
    }

    /**
     * 将匹配提交队列绑定到提交路由键。
     *
     * @param matchmakingSubmitQueue 匹配提交队列
     * @param matchmakingExchange 匹配系统交换机
     * @return 匹配提交队列绑定关系
     */
    @Bean
    public Binding matchmakingSubmitBinding(@Qualifier("matchmakingSubmitQueue") Queue matchmakingSubmitQueue,
                                            TopicExchange matchmakingExchange) {
        return BindingBuilder.bind(matchmakingSubmitQueue)
                .to(matchmakingExchange)
                .with(MatchmakingKey.Submit.routingKey);
    }

    /**
     * 将匹配取消队列绑定到取消路由键。
     *
     * @param matchmakingCancelQueue 匹配取消队列
     * @param matchmakingExchange 匹配系统交换机
     * @return 匹配取消队列绑定关系
     */
    @Bean
    public Binding matchmakingCancelBinding(@Qualifier("matchmakingCancelQueue") Queue matchmakingCancelQueue,
                                            TopicExchange matchmakingExchange) {
        return BindingBuilder.bind(matchmakingCancelQueue)
                .to(matchmakingExchange)
                .with(MatchmakingKey.Cancel.routingKey);
    }
}

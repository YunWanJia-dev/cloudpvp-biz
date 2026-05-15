package me.ywj.cloudpvp.usersummary.configurations;

import me.ywj.cloudpvp.core.utils.JacksonUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * RedisConfiguration
 * 用户资料缓存 Redis 配置。
 *
 * @author sheip9
 * @since 2026/5/15 16:53
 */
@Configuration
@EnableRedisRepositories(basePackages = "me.ywj.cloudpvp.usersummary.repository")
public class RedisConfiguration {
    public static final RedisSerializer<Object> SERIALIZER = new GenericJackson2JsonRedisSerializer(JacksonUtils.INSTANCE);

    /**
     * 创建 RedisTemplate。
     *
     * @param connectionFactory Redis 连接工厂
     * @return RedisTemplate
     */
    @Bean
    public <K, V> RedisTemplate<K, V> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<K, V> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        // key 保持字符串格式，方便直接在 Redis 里排查缓存内容。
        template.setKeySerializer(new StringRedisSerializer());
        // value 复用 core 的 Jackson 配置，避免 Kotlin data class 和枚举映射规则出现两套行为。
        template.setValueSerializer(SERIALIZER);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(SERIALIZER);
        return template;
    }
}

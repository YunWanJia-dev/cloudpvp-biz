package me.ywj.cloudpvp.matchmaking.configure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

/**
 * RedisConfigure
 *
 * @author sheip9
 * @since 2024/2/28 19:16
 */
@Configuration
public class RedisConfigure {
    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        return new LettuceConnectionFactory();
    }
}

package me.ywj.cloudpvp.beans.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.ywj.cloudpvp.core.utils.JacksonUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfiguration {
    @Bean
    public ObjectMapper objectMapper() {
        return JacksonUtils.INSTANCE;
    }
}

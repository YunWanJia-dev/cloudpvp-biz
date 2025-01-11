package me.ywj.cloudpvp.beans.configuration;

import me.ywj.cloudpvp.beans.property.FeishuProperty;
import me.ywj.cloudpvp.core.utils.FeishuWebhookUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeishuConfiguration {
    @Bean
    public FeishuWebhookUtils feishuWebhookUtils(@Autowired FeishuProperty feishuProperty) {
        return new FeishuWebhookUtils(feishuProperty);
    }
}

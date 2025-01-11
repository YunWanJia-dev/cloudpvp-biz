package me.ywj.cloudpvp.beans.property;

import me.ywj.cloudpvp.core.model.configuration.FeishuWebhookConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("cloudpvp.feishu")
public class FeishuProperty extends FeishuWebhookConfiguration {
}

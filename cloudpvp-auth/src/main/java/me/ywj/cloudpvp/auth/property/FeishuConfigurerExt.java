package me.ywj.cloudpvp.auth.property;

import me.ywj.cloudpvp.core.model.configure.FeishuWebhookConfigure;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "cloudpvp.feishu")
public class FeishuConfigurerExt extends FeishuWebhookConfigure {
}

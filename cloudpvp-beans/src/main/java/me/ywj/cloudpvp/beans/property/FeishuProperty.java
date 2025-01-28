package me.ywj.cloudpvp.beans.property;

import me.ywj.cloudpvp.core.model.configuration.FeishuWebhookConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * FeishuProperty
 * 飞书Webhook配置类
 * <p>
 * Example：
 * <pre>
 * cloudpvp:
 *   feishu:
 *     uri: URI_HERE
 *     sign: SIGN_HERE (NULLABLE)
 * </pre>
 */
@Configuration
@ConfigurationProperties("cloudpvp.feishu")
public class FeishuProperty extends FeishuWebhookConfiguration {
}

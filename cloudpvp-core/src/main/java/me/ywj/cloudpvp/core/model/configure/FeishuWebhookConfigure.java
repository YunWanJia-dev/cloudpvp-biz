package me.ywj.cloudpvp.core.model.configure;

import lombok.Builder;
import lombok.Getter;

/**
 * FeishuWebhookConfigure
 *
 * @author sheip9
 * @since 2024/11/14 16:17
 */
@Builder
@Getter
public class FeishuWebhookConfigure {
    private final String uri;
    private final String sign;
}
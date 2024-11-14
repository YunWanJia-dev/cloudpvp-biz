package me.ywj.cloudpvp.core.model.webhook;

import lombok.Getter;

/**
 * FeishuWebhookConfigure
 *
 * @author sheip9
 * @since 2024/11/14 16:17
 */
@Getter
public class FeishuWebhookConfigure {
    private String url;
    private String sign;

    public FeishuWebhookConfigure(String url) {
        this.url = url;
    }

}
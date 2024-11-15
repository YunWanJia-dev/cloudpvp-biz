package me.ywj.cloudpvp.core.model.configure;

import lombok.Builder;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * FeishuWebhookConfigure
 *  飞书webhook机器人配置
 * @author sheip9
 * @since 2024/11/14 16:17
 */
@Builder
@Getter
public class FeishuWebhookConfigure {
    /**
     * url
     * webhook地址
     */
    @NotNull
    private final String uri;
    /**
     * sign
     * 签名校验
     */
    @Nullable
    private final String sign;
}
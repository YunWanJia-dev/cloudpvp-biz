package me.ywj.cloudpvp.core.model.configuration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * FeishuWebhookConfigure
 *  飞书webhook机器人配置
 * @author sheip9
 * @since 2024/11/14 16:17
 */
@Builder
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class FeishuWebhookConfiguration {
    /**
     * url
     * webhook地址
     */
//    @NotNull
    private String uri;
    /**
     * sign
     * 签名校验
     */
//    @Nullable
    private String sign;
}
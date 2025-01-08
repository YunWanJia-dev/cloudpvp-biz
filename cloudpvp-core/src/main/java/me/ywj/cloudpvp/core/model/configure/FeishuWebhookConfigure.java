package me.ywj.cloudpvp.core.model.configure;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
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
@AllArgsConstructor
@NoArgsConstructor
public class FeishuWebhookConfigure {
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
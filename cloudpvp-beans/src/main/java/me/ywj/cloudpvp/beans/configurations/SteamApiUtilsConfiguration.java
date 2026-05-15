package me.ywj.cloudpvp.beans.configurations;

import me.ywj.cloudpvp.beans.property.SteamApiProperty;
import me.ywj.cloudpvp.core.utils.SteamApiUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SteamApiUtilsConfiguration
 * Steam API 工具配置类。
 *
 * @author sheip9
 * @since 2026/5/15 16:49
 */
@Configuration
public class SteamApiUtilsConfiguration {
    /**
     * 创建 Steam API 请求工具 bean。
     *
     * @param steamApiProperty Steam API 配置属性
     * @return Steam API 请求工具
     */
    @Bean
    public SteamApiUtils steamApiUtils(SteamApiProperty steamApiProperty) {
        // Spring 负责绑定和注入配置，core 工具类只接收普通配置对象以保持框架无关。
        return new SteamApiUtils(steamApiProperty);
    }
}

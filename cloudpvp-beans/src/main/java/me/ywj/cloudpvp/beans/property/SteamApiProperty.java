package me.ywj.cloudpvp.beans.property;

import me.ywj.cloudpvp.core.model.configurations.SteamApiConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * SteamApiProperty
 * steam api 密钥配置
 *
 * Example：
 * ```
 * cloudpvp:
 *   steam:
 *      api:
 *          key: xxxxx
 *  ```
 * @author sheip9
 * @since 2026/5/15
 **/
@Configuration
@ConfigurationProperties("cloudpvp.steam.api")
public class SteamApiProperty extends SteamApiConfiguration {
}

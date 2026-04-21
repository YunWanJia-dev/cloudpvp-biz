package me.ywj.cloudpvp.auth.configurations;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * SteamOpenIDEndpointConfiguration
 *
 * @author sheip9
 * @since 2026/4/15
 **/
@ConfigurationProperties("cloudpvp.steam.openid")
@AllArgsConstructor
@Getter
public class SteamOpenIDEndpointConfiguration {
    @Nullable
    private final String override;
}
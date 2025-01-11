package me.ywj.cloudpvp.beans.property;

import me.ywj.cloudpvp.core.model.configuration.JWTConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("cloudpvp.jwt")
public class JWTProperty extends JWTConfiguration {
}

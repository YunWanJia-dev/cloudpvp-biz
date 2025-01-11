package me.ywj.cloudpvp.beans.property;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("cloudpvp.jwt")
public class JWTProperty {
}

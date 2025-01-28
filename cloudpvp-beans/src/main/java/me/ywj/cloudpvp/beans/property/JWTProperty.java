package me.ywj.cloudpvp.beans.property;

import me.ywj.cloudpvp.core.model.configuration.JWTConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * JWTProperty
 * JWT配置类
 * <p>
 * Example：
 * <pre>
 * cloudpvp:
 *   jwt:
 *     secret: SECRET_HERE
 *  </pre>
 */
@Configuration
@ConfigurationProperties("cloudpvp.jwt")
public class JWTProperty extends JWTConfiguration {
}

package me.ywj.cloudpvp.beans.configuration;

import me.ywj.cloudpvp.beans.property.JWTProperty;
import me.ywj.cloudpvp.core.utils.JWTUtils;
import me.ywj.cloudpvp.core.utils.TokenUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TokenUtilsConfiguration {
    @Bean
    public TokenUtils tokenUtils(@Autowired JWTProperty jwtProperty) {
        TokenUtils bean = new TokenUtils();
        bean.setJwtUtils(new JWTUtils(jwtProperty));
        return bean;
    }
}

package me.ywj.cloudpvp.beans.configuration;

import me.ywj.cloudpvp.beans.property.JWTProperty;
import me.ywj.cloudpvp.core.service.JwtTokenService;
import me.ywj.cloudpvp.core.service.TokenService;
import me.ywj.cloudpvp.core.utils.JWTUtils;
import me.ywj.cloudpvp.core.utils.TokenUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * TokenUtilsConfiguration
 * 令牌相关配置类
 *
 * @author sheip9
 * @since 2026/5/15 13:54
 */
@Configuration
public class TokenUtilsConfiguration {
    /**
     * jwtUtils
     * 创建 JWT 底层工具 bean。
     *
     * @param jwtProperty JWT 配置属性
     * @return JWT 工具
     */
    @Bean
    public JWTUtils jwtUtils(JWTProperty jwtProperty) {
        return new JWTUtils(jwtProperty);
    }

    /**
     * tokenService
     * 创建业务 token 服务 bean。
     *
     * @param jwtUtils JWT 底层工具
     * @return token 服务接口实现
     */
    @Bean
    public TokenService tokenService(JWTUtils jwtUtils) {
        return new JwtTokenService(jwtUtils);
    }

    /**
     * tokenUtils
     * 创建旧版 TokenUtils 兼容 bean。
     *
     * @param jwtUtils JWT 底层工具
     * @return 旧版 token 工具
     */
    @Bean
    public TokenUtils tokenUtils(JWTUtils jwtUtils) {
        TokenUtils bean = new TokenUtils();
        bean.setJwtUtils(jwtUtils);
        return bean;
    }
}

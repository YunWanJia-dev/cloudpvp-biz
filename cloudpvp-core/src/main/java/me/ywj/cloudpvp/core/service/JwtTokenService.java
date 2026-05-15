package me.ywj.cloudpvp.core.service;

import me.ywj.cloudpvp.core.constant.header.Attributes;
import me.ywj.cloudpvp.core.utils.JWTUtils;

import java.util.Map;

/**
 * JwtTokenService
 * 基于令牌的令牌服务实现类
 *
 * @author sheip9
 * @since 2026/5/15 13:54
 */
public class JwtTokenService implements TokenService {
    private final JWTUtils jwtUtils;

    /**
     * JwtTokenService
     * 创建 JWT token 服务。
     *
     * @param jwtUtils JWT 底层工具
     */
    public JwtTokenService(JWTUtils jwtUtils) {
        this.jwtUtils = jwtUtils;
    }

    /**
     * generateToken
     * 根据用户ID生成 JWT token。
     *
     * @param userId 用户ID
     * @return 生成的 JWT token
     */
    @Override
    public String generateToken(Long userId) {
        return jwtUtils.generateToken(Map.of(Attributes.ID, userId));
    }

    /**
     * validateToken
     * 校验 JWT token 的签名和有效期。
     *
     * @param token 待校验的 JWT token
     * @return token 签名正确且未过期时返回 true
     */
    @Override
    public boolean validateToken(String token) {
        return jwtUtils.validateToken(token);
    }

    /**
     * getIDFromToken
     * 从验签通过的 JWT token 中获取用户ID。
     *
     * @param token JWT token
     * @return token 中的用户ID
     */
    @Override
    public Long getIDFromToken(String token) {
        return jwtUtils.getClaim(token, Attributes.ID);
    }
}

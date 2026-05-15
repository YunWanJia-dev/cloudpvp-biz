package me.ywj.cloudpvp.core.utils;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.exceptions.JWTVerificationException;
import me.ywj.cloudpvp.core.model.configuration.JWTConfiguration;

import java.util.Date;
import java.util.Map;

/**
 * JWTUtils
 * 令牌底层工具类
 *
 * @author sheip9
 * @since 2025/1/11 23:00
 */
public class JWTUtils {
    private final JWTConfiguration configuration;

    /**
     * JWTUtils
     * 创建 JWT 工具类。
     *
     * @param configuration JWT 配置
     */
    public JWTUtils(JWTConfiguration configuration) {
        this.configuration = configuration;
    }

    /**
     * generateToken
     * 根据 claims 生成 JWT token。
     *
     * @param claims 需要写入 token 的 Long 类型 claims
     * @return 生成的 JWT token
     */
    public String generateToken(Map<String, Long> claims) {
        JWTCreator.Builder builder = JWT.create();
        claims.forEach(builder::withClaim);
        builder.withIssuedAt(new Date(System.currentTimeMillis()));
        builder.withExpiresAt(new Date(System.currentTimeMillis() + configuration.getExpireTime()));
        return builder.sign(crypt(configuration.getSecret()));
    }

    /**
     * crypt
     * 根据密钥创建 HMAC256 签名算法。
     *
     * @param str 签名密钥
     * @return HMAC256 算法实例
     */
    private Algorithm crypt(String str) {
        return Algorithm.HMAC256(str);
    }

    /**
     * verifyToken
     * 校验 token 签名和有效期。
     *
     * @param token 待校验的 JWT token
     * @return 验签通过后的 JWT
     */
    private DecodedJWT verifyToken(String token) {
        return JWT.require(crypt(configuration.getSecret())).build().verify(token);
    }

    /**
     * getClaim
     * 验签后读取指定 claim。
     *
     * @param token JWT token
     * @param claimName claim 名称
     * @return claim 对应的 Long 值
     */
    public Long getClaim(String token, String claimName) {
        return verifyToken(token).getClaim(claimName).asLong();
    }

    /**
     * validateToken
     * 校验 token 的签名和有效期。
     *
     * @param token 待校验的 JWT token
     * @return token 验签通过且未过期时返回 true
     */
    public boolean validateToken(String token) {
        try {
            verifyToken(token);
            return true;
        } catch (JWTVerificationException | IllegalArgumentException e) {
            return false;
        }
    }
}

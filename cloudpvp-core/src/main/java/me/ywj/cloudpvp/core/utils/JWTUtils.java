package me.ywj.cloudpvp.core.utils;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import me.ywj.cloudpvp.core.model.configuration.JWTConfiguration;

import java.util.Date;
import java.util.Map;

/**
 * JWTUtils
 *
 * @author sheip9
 * @since 2025/1/11 23:00
 */
public class JWTUtils {
    private final JWTConfiguration configuration;

    public JWTUtils(JWTConfiguration configuration) {
        this.configuration = configuration;
    }

    public String generateToken(Map<String, Long> claims) {
        JWTCreator.Builder builder = JWT.create();
        claims.forEach(builder::withClaim);
        builder.withIssuedAt(new Date(System.currentTimeMillis()));
        builder.withExpiresAt(new Date(System.currentTimeMillis() + configuration.getExpireTime()));
        return builder.sign(crypt(configuration.getSecret()));
    }

    private Algorithm crypt(String str) {
        return Algorithm.HMAC256(str);
    }

    public Long getClaim(String token, String claimName) {
        return JWT.decode(token).getClaim(claimName).asLong();
    }

    public boolean validateToken(String token) {
        try {
            return JWT.decode(token).getExpiresAt().before(new Date());
        } catch (JWTVerificationException e) {
            return false;
        }
    }
}

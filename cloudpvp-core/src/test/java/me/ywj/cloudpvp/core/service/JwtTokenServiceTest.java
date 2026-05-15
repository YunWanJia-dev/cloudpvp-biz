package me.ywj.cloudpvp.core.service;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.ywj.cloudpvp.core.constant.header.Attributes;
import me.ywj.cloudpvp.core.model.configuration.JWTConfiguration;
import me.ywj.cloudpvp.core.utils.JWTUtils;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JwtTokenServiceTest
 * 令牌服务测试类
 *
 * @author sheip9
 * @since 2026/5/15 13:54
 */
class JwtTokenServiceTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Long USER_ID = 76561198842604564L;

    /**
     * generatedTokenIsValidAndContainsId
     * 验证新生成 token 可通过校验并能读取用户ID。
     */
    @Test
    void generatedTokenIsValidAndContainsId() {
        TokenService tokenService = tokenService("test-secret", 60_000L);

        String token = tokenService.generateToken(USER_ID);

        assertTrue(tokenService.validateToken(token));
        assertEquals(USER_ID, tokenService.getIDFromToken(token));
    }

    /**
     * expiredTokenIsRejected
     * 验证过期 token 会被拒绝。
     */
    @Test
    void expiredTokenIsRejected() {
        TokenService tokenService = tokenService("test-secret", -1_000L);

        String token = tokenService.generateToken(USER_ID);

        assertFalse(tokenService.validateToken(token));
        assertThrows(JWTVerificationException.class, () -> tokenService.getIDFromToken(token));
    }

    /**
     * tokenSignedWithWrongSecretIsRejected
     * 验证错误密钥签发的 token 会被拒绝。
     */
    @Test
    void tokenSignedWithWrongSecretIsRejected() {
        TokenService tokenService = tokenService("test-secret", 60_000L);
        TokenService foreignTokenService = tokenService("other-secret", 60_000L);

        String token = foreignTokenService.generateToken(USER_ID);

        assertFalse(tokenService.validateToken(token));
        assertThrows(JWTVerificationException.class, () -> tokenService.getIDFromToken(token));
    }

    /**
     * tamperedPayloadIsRejected
     * 验证篡改 payload 后的 token 会被拒绝。
     *
     * @throws Exception JSON 解析或编码异常
     */
    @Test
    void tamperedPayloadIsRejected() throws Exception {
        TokenService tokenService = tokenService("test-secret", 60_000L);
        String token = tokenService.generateToken(USER_ID);

        String tamperedToken = tamperClaim(token, 76561198842604565L);

        assertFalse(tokenService.validateToken(tamperedToken));
        assertThrows(JWTVerificationException.class, () -> tokenService.getIDFromToken(tamperedToken));
    }

    /**
     * malformedTokenIsRejected
     * 验证空 token 和格式错误 token 会被拒绝。
     */
    @Test
    void malformedTokenIsRejected() {
        TokenService tokenService = tokenService("test-secret", 60_000L);

        assertFalse(tokenService.validateToken(null));
        assertFalse(tokenService.validateToken("not-a-token"));
    }

    /**
     * tokenService
     * 根据指定密钥和有效期创建测试用 token 服务。
     *
     * @param secret JWT 签名密钥
     * @param expireTime JWT 有效期毫秒数
     * @return 测试用 token 服务
     */
    private static TokenService tokenService(String secret, long expireTime) {
        JWTConfiguration configuration = new JWTConfiguration();
        configuration.setSecret(secret);
        configuration.setExpireTime(expireTime);
        return new JwtTokenService(new JWTUtils(configuration));
    }

    /**
     * tamperClaim
     * 修改 token payload 中的用户ID但保留原签名。
     *
     * @param token 原始 JWT token
     * @param userId 替换后的用户ID
     * @return payload 被篡改后的 token
     * @throws Exception JSON 解析或编码异常
     */
    @SuppressWarnings("unchecked")
    private static String tamperClaim(String token, long userId) throws Exception {
        String[] parts = token.split("\\.");
        assertEquals(3, parts.length);

        byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
        Map<String, Object> payload = OBJECT_MAPPER.readValue(payloadBytes, Map.class);
        payload.put(Attributes.ID, userId);

        String changedPayload = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(OBJECT_MAPPER.writeValueAsBytes(payload));
        return parts[0] + "." + changedPayload + "." + parts[2];
    }
}

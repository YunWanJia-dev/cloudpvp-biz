package me.ywj.cloudpvp.beans.utils;

import me.ywj.cloudpvp.beans.exceptions.UserIdInvalidException;
import me.ywj.cloudpvp.core.service.TokenService;
import org.springframework.stereotype.Component;

/**
 * TokenAuthUtils
 * 令牌鉴权工具类
 *
 * @author sheip9
 * @since 2026/5/15 13:54
 */
@Component
public class TokenAuthUtils {
    private final TokenService tokenService;

    public TokenAuthUtils(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    /**
     * getIDFromToken
     * 校验令牌并获取用户id。
     *
     * @param token 令牌
     * @return 用户id
     * @throws UserIdInvalidException 用户id无效异常
     */
    public Long getIDFromToken(String token) throws UserIdInvalidException {
        if (!tokenService.validateToken(token)) {
            throw new UserIdInvalidException();
        }
        Long id = tokenService.getIDFromToken(token);
        if (id == null) {
            throw new UserIdInvalidException();
        }
        return id;
    }
}

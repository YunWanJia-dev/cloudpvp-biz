package me.ywj.cloudpvp.core.service;

/**
 * TokenService
 * 令牌服务接口
 *
 * @author sheip9
 * @since 2026/5/15 13:54
 */
public interface TokenService {
    /**
     * generateToken
     * 根据用户ID生成业务 token。
     *
     * @param userId 用户ID
     * @return 生成的 token
     */
    String generateToken(Long userId);

    /**
     * validateToken
     * 校验 token 是否有效。
     *
     * @param token 待校验的 token
     * @return token 有效时返回 true
     */
    boolean validateToken(String token);

    /**
     * getIDFromToken
     * 从 token 中获取用户ID。
     *
     * @param token 已通过鉴权体系签发的 token
     * @return token 中的用户ID
     */
    Long getIDFromToken(String token);
}

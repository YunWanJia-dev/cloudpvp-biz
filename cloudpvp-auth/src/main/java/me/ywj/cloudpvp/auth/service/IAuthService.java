package me.ywj.cloudpvp.auth.service;

/**
 * IAuthService
 * Token的生成和校验
 * @author sheip9
 * @since 2024/3/8 14:53
 */
public interface IAuthService {
    /**
     * generateToken
     * 生成token并存入Redis
     * @param userId
     * @return
     */
    String generateToken(String userId);
}

package me.ywj.cloudpvp.auth.service;

/**
 * ISteamAuthService
 *
 * @author sheip9
 * @since 2024/1/19 11:47
 */
public interface ISteamAuthService {
    /**
     * generateSteamLoginUrl
     * 生成steam登录的地址
     * @param hostname 登录后重定向的域名
     * @param router 登录后重定向的路由
     * @return steam openid登录地址
     */
    String generateSteamLoginUrl(String hostname, String router);

    /**
     * validRequestFromUser
     * 校验用户的steam登录是否有效
     * @param openidAccocHandler
     * @param openidSigned
     * @param openidSig
     * @param openidNs
     * @param openidMode
     * @param openidOpEndpoint
     * @param openidClaimedId
     * @param openidIdentity
     * @param openidReturnTo
     * @param openidResponseNonce
     * @return 是否有效
     */
    boolean validRequestFromUser(
            String openidAccocHandler,
            String openidSigned,
            String openidSig,
            String openidNs,
            String openidMode,
            String openidOpEndpoint,
            String openidClaimedId,
            String openidIdentity,
            String openidReturnTo,
            String openidResponseNonce
    );
}

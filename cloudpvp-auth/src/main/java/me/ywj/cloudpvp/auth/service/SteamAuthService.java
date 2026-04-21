package me.ywj.cloudpvp.auth.service;

import lombok.AllArgsConstructor;
import me.ywj.cloudpvp.auth.exceptions.InternalErrorException;
import me.ywj.cloudpvp.auth.model.SteamOpenIDVerificationResult;
import me.ywj.cloudpvp.auth.model.TokenModel;
import me.ywj.cloudpvp.auth.utils.SteamOpenIDUtils;
import me.ywj.cloudpvp.core.utils.TokenUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URISyntaxException;

/**
 * SteamAuthServiceImpl
 *
 * @author sheip9
 * @since 2024/1/19 11:47
 */
@Service
@AllArgsConstructor(onConstructor = @__(@Autowired))
public class SteamAuthService {
    private final TokenUtils tokenUtils;
    private final SteamOpenIDUtils steamOpenIDUtils;

    /**
     * generateSteamLoginUrl
     * 生成用于重定向至的URL
     *
     * @param hostname 重定向目标域名
     * @param router   重定向目标路径
     * @return 重定向至steam的URL
     */
    public String generateSteamLoginUrl(String hostname, String router) {
        String baseUrl =
                "https://steamcommunity.com/openid/login?" +
                        "openid.ns=http://specs.openid.net/auth/2.0&openid.mode=checkid_setup" +
                        "&openid.return_to=http://%s%s" +
                        "&openid.realm=http://%s%s" +
                        "&openid.identity=http://specs.openid.net/auth/2.0/identifier_select" +
                        "&openid.claimed_id=http://specs.openid.net/auth/2.0/identifier_select";
        return String.format(baseUrl, hostname, router, hostname, router);
    }

    /**
     * validRequestFromUser
     * 校验完成steam登录后重定向过来的请求是否有效
     *
     * @return 是否有效
     */
    public TokenModel validRequestFromUser(
            String openidAccOcHandler,
            String openidSigned,
            String openidSig,
            String openidNs,
            String openidMode,
            String openidOpEndpoint,
            String openidClaimedId,
            String openidIdentity,
            String openidReturnTo,
            String openidResponseNonce
    ) {
        try {
            // 调用 SteamOpenIDUtils 进行验证
            SteamOpenIDVerificationResult result = steamOpenIDUtils.verifying(
                    openidNs,
                    openidMode,
                    openidOpEndpoint,
                    openidClaimedId,
                    openidIdentity,
                    openidReturnTo,
                    openidResponseNonce,
                    openidAccOcHandler,
                    openidSigned,
                    openidSig
            );

            // 验证失败
            if (!result.isValid()) {
                return null;
            }

            // 验证成功，生成 token
            String token = tokenUtils.generateToken(result.getSteamId64());
            return new TokenModel(token);

        } catch (MalformedURLException | ProtocolException e) {
            throw new InternalErrorException("内部逻辑发生错误");
        } catch (IOException e) {
            e.printStackTrace();
            throw new InternalErrorException("获取返回内容发生错误");
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

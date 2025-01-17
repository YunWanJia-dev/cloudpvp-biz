package me.ywj.cloudpvp.auth.service;

import lombok.AllArgsConstructor;
import me.ywj.cloudpvp.auth.exceptions.InternalErrorException;
import me.ywj.cloudpvp.auth.model.TokenModel;
import me.ywj.cloudpvp.core.utils.HttpUtils;
import me.ywj.cloudpvp.core.utils.TokenUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;

/**
 * SteamAuthServiceImpl
 *
 * @author sheip9
 * @since 2024/1/19 11:47
 */
@Service
@AllArgsConstructor(onConstructor = @__(@Autowired))
public class SteamAuthService {
    private final HttpUtils httpUtils = new HttpUtils(
            HttpRequest.newBuilder()
                    .uri(URI.create("https://steamcommunity.com/openid/login?"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .build()
    );
    private final TokenUtils tokenUtils;

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
            String params =
                    "openid.assoc_handle=" + openidAccOcHandler +
                            "&openid.signed=" + openidSigned +
                            "&openid.sig=" + openidSig +
                            "&openid.ns=" + openidNs +
                            "&openid.mode=" + openidMode +
                            "&openid.op_endpoint=" + openidOpEndpoint +
                            "&openid.claimed_id=" + openidClaimedId +
                            "&openid.identity=" + openidIdentity +
                            "&openid.return_to=" + openidReturnTo +
                            "&openid.response_nonce=" + openidResponseNonce +
                            "&openid.mode=check_authentication";
            var r = httpUtils.get(params);
            String resp = r.body();
            // 读取返回内容，获取is_valid的值
            // e.g:
            // ns:http://specs.openid.net/auth/2.0
            // is_valid:true
            var str = resp.substring(resp.length() - 5, resp.length() - 1);
            boolean validation = "true".equals(str);
            if (validation) {
                String token = tokenUtils.generateToken(Long.valueOf(openidIdentity.replace("https://steamcommunity.com/openid/id/", "")));
                return new TokenModel(token);
            }
            return null;
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

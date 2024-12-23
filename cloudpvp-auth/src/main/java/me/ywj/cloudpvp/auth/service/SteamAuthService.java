package me.ywj.cloudpvp.auth.service;

import me.ywj.cloudpvp.auth.exceptions.InternalErrorException;
import me.ywj.cloudpvp.core.utils.HttpUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.*;
import java.net.http.HttpRequest;

/**
 * SteamAuthServiceImpl
 *
 * @author sheip9
 * @since 2024/1/19 11:47
 */
@Service
public class SteamAuthService {
    private final HttpUtils httpUtils = new HttpUtils(
            HttpRequest.newBuilder()
                    .uri(URI.create("https://steamcommunity.com/openid/login?"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .build()
    );
    /**
     * generateSteamLoginUrl
     * 生成用于重定向至的URL
     * @param hostname 重定向目标域名
     * @param router 重定向目标路径
     * @return 重定向至steam的URL
     */
    public String generateSteamLoginUrl(String hostname, String router) {
        String baseUrl =
                "https://steamcommunity.com/openid/login?" +
                "openid.ns=http://specs.openid.net/auth/2.0&openid.mode=checkid_setup" +
                "&openid.return_to=http://%s%s" +
                "&openid.realm=http://%s%s" +
                "&openid.identity=http://specs.openid.net/auth/2.0/identifier_select" +
                "&openid.claimed_id=http://specs.openid.net/auth/2.0/identifier_select"
                ;
        return String.format(baseUrl, hostname, router, hostname, router);
    }

    /**
     * validRequestFromUser
     * 校验完成steam登录后重定向过来的请求是否有效
     * @return 是否有效
     */
    public boolean validRequestFromUser(
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
    ){
        try {
            String params =
                    "openid.assoc_handle="    + openidAccOcHandler +
                    "&openid.signed="         + openidSigned +
                    "&openid.sig="            + openidSig +
                    "&openid.ns="             + openidNs +
                    "&openid.mode="           + openidMode +
                    "&openid.op_endpoint="    + openidOpEndpoint +
                    "&openid.claimed_id="     + openidClaimedId +
                    "&openid.identity="       + openidIdentity +
                    "&openid.return_to="      + openidReturnTo +
                    "&openid.response_nonce=" + openidResponseNonce +
                    "&openid.mode=check_authentication";
            String resp = httpUtils.get(params).body();
            // 读取返回内容，获取is_valid的值
            // e.g:
            // ns:http://specs.openid.net/auth/2.0
            // is_valid:true
            var str = resp.substring(resp.length() - 5, resp.length() - 1);
            return "true".equals(str);
        } catch (MalformedURLException | ProtocolException e) {
            throw new InternalErrorException("内部逻辑发生错误");
        } catch (IOException e) {
            throw new InternalErrorException("获取返回内容发生错误");
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

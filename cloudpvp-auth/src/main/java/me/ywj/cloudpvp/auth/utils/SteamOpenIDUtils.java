package me.ywj.cloudpvp.auth.utils;

import me.ywj.cloudpvp.auth.configurations.SteamOpenIDEndpointConfiguration;
import me.ywj.cloudpvp.auth.model.SteamOpenIDVerificationResult;
import me.ywj.cloudpvp.core.constant.steam.SteamLoginUrl;
import me.ywj.cloudpvp.core.utils.HttpUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpRequest;
import java.util.List;
import java.util.Map;

/**
 * SteamOpenIDUtils
 *
 * @author sheip9
 * @since 2026/4/15
 **/
@Component
public class SteamOpenIDUtils {
    private static final String STEAM_IDENTITY_PREFIX = "https://steamcommunity.com/openid/id/";

    private final HttpUtils httpUtils;

    @Autowired
    public SteamOpenIDUtils(SteamOpenIDEndpointConfiguration configuration) {
        String url = configuration.getOverride() != null ? configuration.getOverride() : SteamLoginUrl.OPENID_URL_BASE;
        httpUtils = new HttpUtils(
                HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .build()
        );
    }

    /**
     * 验证 Steam OpenID 回调请求
     *
     * @param openidNs            OpenID 命名空间
     * @param openidMode          OpenID 模式
     * @param openidOpEndpoint    OpenID 端点
     * @param openidClaimedId     声明的 ID
     * @param openidIdentity      身份标识
     * @param openidReturnTo      返回地址
     * @param openidResponseNonce 响应随机数
     * @param openidAssocHandle   关联句柄
     * @param openidSigned        签名字段
     * @param openidSig           签名
     * @return 验证结果
     * @throws Exception 网络请求异常
     */
    public SteamOpenIDVerificationResult verifying(
            String openidNs,
            String openidMode,
            String openidOpEndpoint,
            String openidClaimedId,
            String openidIdentity,
            String openidReturnTo,
            String openidResponseNonce,
            String openidAssocHandle,
            String openidSigned,
            String openidSig
    ) throws Exception {
        // 构建验证参数列表
        List<Map.Entry<String, String>> params = List.of(
                Map.entry("openid.ns", openidNs),
                Map.entry("openid.mode", "check_authentication"),
                Map.entry("openid.op_endpoint", openidOpEndpoint),
                Map.entry("openid.claimed_id", openidClaimedId),
                Map.entry("openid.identity", openidIdentity),
                Map.entry("openid.return_to", openidReturnTo),
                Map.entry("openid.response_nonce", openidResponseNonce),
                Map.entry("openid.assoc_handle", openidAssocHandle),
                Map.entry("openid.signed", openidSigned),
                Map.entry("openid.sig", openidSig)
        );

        // 发送验证请求到 Steam
        var response = httpUtils.get(SteamLoginUrl.OPENID_LOGIN_PATH, params);
        String responseBody = response.body();

        // 解析响应，检查是否验证成功
        boolean isValid = responseBody.contains("is_valid:true");

        if (!isValid) {
            return SteamOpenIDVerificationResult.invalid();
        }

        // 从 identity URL 提取 Steam ID
        if (!openidIdentity.startsWith(STEAM_IDENTITY_PREFIX)) {
            throw new IllegalArgumentException("Invalid Steam identity URL: " + openidIdentity);
        }

        Long steamId64 = Long.valueOf(openidIdentity.substring(STEAM_IDENTITY_PREFIX.length()));

        return SteamOpenIDVerificationResult.success(steamId64);
    }
}

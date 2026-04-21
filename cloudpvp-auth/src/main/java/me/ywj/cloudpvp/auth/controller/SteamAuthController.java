package me.ywj.cloudpvp.auth.controller;

import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import me.ywj.cloudpvp.auth.service.SteamAuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Optional;

/**
 * SteamOpenIDController
 *
 * @author sheip9
 * @since 2024/1/18 23:51
 */
@RestController
@RequestMapping("/steam")
@AllArgsConstructor(onConstructor = @__(@Autowired))
public class SteamAuthController {
    private final SteamAuthService steamAuthService;
    private static final String FINALLY_REDIRECT_FIELD = "finallyRedirection";
    /**
     * redirectToSteam
     * 重定向至steam以登录
     */
    @GetMapping("/redirect_to_steam")
    public void redirectToSteam(@RequestHeader(value = "Host") String host, HttpServletResponse response, @RequestParam(FINALLY_REDIRECT_FIELD) Optional<String> finallyRedirection) throws IOException {
        String redirectUrl = steamAuthService.generateSteamLoginUrl(host, "/auth/steam/login");
        response.sendRedirect(redirectUrl);
    }

    /**
     * redirectToSteam
     * 重定向至steam以登录
     */
    @PostMapping("/redirect_to_steam")
    public String redirectToSteam(@RequestHeader(value = "Host") String host, @RequestParam(FINALLY_REDIRECT_FIELD) Optional<String> finallyRedirection) {
        return steamAuthService.generateSteamLoginUrl(host, "/auth/steam/login");
    }

    /**
     * receiveReturnFromSteam
     * 接受完成steam登录后的重定向请求
     */
    @GetMapping("/login")
    public Object receiveReturnFromSteam(
            @RequestParam("openid.assoc_handle") String openidAccOcHandler,
            @RequestParam("openid.signed") String openidSigned,
            @RequestParam("openid.sig") String openidSig,
            @RequestParam("openid.ns") String openidNs,
            @RequestParam("openid.mode") String openidMode,
            @RequestParam("openid.op_endpoint") String openidOpEndpoint,
            @RequestParam("openid.claimed_id") String openidClaimedId,
            @RequestParam("openid.identity") String openidIdentity,
            @RequestParam("openid.return_to") String openidReturnTo,
            @RequestParam("openid.response_nonce") String openidResponseNonce,
            @RequestParam(FINALLY_REDIRECT_FIELD) Optional<String> finallyRedirection,
            HttpServletResponse response
    ) {
        final var token = steamAuthService.validRequestFromUser(
                openidAccOcHandler,
                openidSigned,
                openidSig,
                openidNs,
                openidMode,
                openidOpEndpoint,
                openidClaimedId,
                openidIdentity,
                openidReturnTo,
                openidResponseNonce
        );
        if (finallyRedirection.isPresent()) {
            try {
                response.sendRedirect(finallyRedirection.get() + "?token=" + token.getToken());
                return null;
            } catch (IOException ignored) {
            }
        }
        return token;
    }
}

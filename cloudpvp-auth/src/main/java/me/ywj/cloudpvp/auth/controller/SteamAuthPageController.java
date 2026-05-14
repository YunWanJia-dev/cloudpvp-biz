package me.ywj.cloudpvp.auth.controller;

import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import me.ywj.cloudpvp.auth.exceptions.SteamLoginException;
import me.ywj.cloudpvp.auth.model.TokenModel;
import me.ywj.cloudpvp.auth.service.SteamAuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import java.io.IOException;
import java.util.Optional;

/**
 * SteamOpenIDController
 *
 * @author sheip9
 * @since 2026/5/13 23:51
 */
@Controller
@RequestMapping("/steam")
@AllArgsConstructor(onConstructor = @__(@Autowired))
public class SteamAuthPageController {
    private final SteamAuthService steamAuthService;
    private static final String FINALLY_REDIRECT_FIELD = "finallyRedirection";

    /**
     * receiveReturnFromSteam
     * 接受完成steam登录后的重定向请求
     */
    @GetMapping("/login")
    public ModelAndView receiveReturnFromSteam(
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
        try {
            final TokenModel token = steamAuthService.validRequestFromUser(
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
            Optional<String> targetRedirection = finallyRedirection.filter(redirection -> !redirection.isBlank());
            if (targetRedirection.isPresent()) {
                String redirectUrl = targetRedirection.get() + "?token=" + token.getToken();
                try {
                    response.sendRedirect(redirectUrl);
                    return null;
                } catch (IOException ignored) {
                    return steamAuthPostMessage(token, redirectUrl);
                }
            }
            return steamAuthPostMessage(token);
        } catch (SteamLoginException e) {
            return steamAuthError(e.getMessage());
        }
    }

    /**
     * 渲染错误页
     * @param errorMessage 错误消息
     * @return thymeleaf
     */
    private ModelAndView steamAuthError(String errorMessage) {
        ModelAndView mv = new ModelAndView("error");
        mv.addObject("errorMessage", errorMessage);
        return mv;
    }

    /**
     * 渲染登录成功页
     * @param token 颁发token
     * @return thymeleaf
     */
    private ModelAndView steamAuthPostMessage(TokenModel token) {
        return steamAuthPostMessage(token, "");
    }

    /**
     * 渲染登录成功页
     * @param token 颁发token
     * @param redirectUrl 跳转地址
     * @return thymeleaf
     */
    private ModelAndView steamAuthPostMessage(TokenModel token, String redirectUrl) {
        ModelAndView mv = new ModelAndView("login-success");
        mv.addObject("token", token.getToken());
        mv.addObject("redirectUrl", redirectUrl);
        return mv;
    }
}

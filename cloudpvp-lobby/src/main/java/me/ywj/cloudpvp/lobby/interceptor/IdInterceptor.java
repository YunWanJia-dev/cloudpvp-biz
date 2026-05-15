package me.ywj.cloudpvp.lobby.interceptor;

import me.ywj.cloudpvp.core.constant.header.Attributes;
import me.ywj.cloudpvp.core.service.TokenService;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

import java.util.Map;

/**
 * IdInterceptor
 * 玩家编号握手拦截器
 *
 * @author sheip9
 * @since 2024/10/25 14:56
 */
@Component
public class IdInterceptor extends HttpSessionHandshakeInterceptor {
    private final TokenService tokenService;

    /**
     * IdInterceptor
     * 创建玩家ID握手拦截器。
     *
     * @param tokenService token 鉴权服务
     */
    public IdInterceptor(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    /**
     * beforeHandshake
     * 在 websocket 握手前校验 token 并写入玩家ID。
     *
     * @param request websocket 握手请求
     * @param response websocket 握手响应
     * @param wsHandler websocket 处理器
     * @param attributes websocket session 属性
     * @return 允许握手时返回 true
     * @throws Exception 父类握手处理异常
     */
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, @NotNull ServerHttpResponse response, @NotNull WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        String token = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (!tokenService.validateToken(token)) {
            return false;
        }
        Long id = tokenService.getIDFromToken(token);
        if (id == null) {
            return false;
        }
        attributes.put(Attributes.ID, id);
        return super.beforeHandshake(request, response, wsHandler, attributes);
    }
}

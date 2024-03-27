package me.ywj.cloudpvp.matchmaking.websocket.interceptor;

import org.jetbrains.annotations.NotNull;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

import java.util.Map;
import java.util.Objects;

/**
 * GlobalHandshakeInterceptor
 *
 * @author sheip9
 * @since 2024/2/28 19:20
 */
public class GlobalHandshakeInterceptor extends HttpSessionHandshakeInterceptor {
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, @NotNull ServerHttpResponse response, @NotNull WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        System.out.println(request.getHeaders().get("playerId"));
        attributes.put("playerId", Objects.requireNonNull(request.getHeaders().get("playerId")).get(0));
        return super.beforeHandshake(request, response, wsHandler, attributes);
    }
}

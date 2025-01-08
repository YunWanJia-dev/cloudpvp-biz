package me.ywj.cloudpvp.lobby.interceptor;

import me.ywj.cloudpvp.core.utils.TokenUtils;
import me.ywj.cloudpvp.core.constant.header.Attributes;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

import java.util.Map;

/**
 * IdInterceptor
 *
 * @author sheip9
 * @since 2024/10/25 14:56
 */
public class IdInterceptor extends HttpSessionHandshakeInterceptor {
    private final TokenUtils tokenUtils = new TokenUtils();
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, @NotNull ServerHttpResponse response, @NotNull WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        Long id = tokenUtils.getIDFromToken(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        attributes.put(Attributes.ID, id);
        return super.beforeHandshake(request, response, wsHandler, attributes);
    }
}

package me.ywj.cloudpvp.lobby.configure;

import me.ywj.cloudpvp.lobby.interceptor.IdInterceptor;
import me.ywj.cloudpvp.lobby.websocket.LobbySocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

import java.util.Map;
import java.util.Objects;

/**
 * WebsocketConfigure
 *
 * @author sheip9
 * @since 2024/10/20 15:44
 */
@Configuration
@EnableWebSocket
public class WebsocketConfigure implements WebSocketConfigurer {
    private final LobbySocketHandler lobbySocketHandler;
    @Autowired
    public WebsocketConfigure(LobbySocketHandler lobbySocketHandler) {
        this.lobbySocketHandler = lobbySocketHandler;
    }
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(lobbySocketHandler, LobbySocketHandler.PATH).addInterceptors(new IdInterceptor()).setAllowedOrigins("*");
    }
}

package me.ywj.cloudpvp.matchmaking.websocket.configurer;

import me.ywj.cloudpvp.matchmaking.websocket.handler.MatchingHandler;
import me.ywj.cloudpvp.matchmaking.websocket.interceptor.GlobalHandshakeInterceptor;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

@Configuration
@EnableWebSocket
public class MatchingConfig implements WebSocketConfigurer {
    MatchingHandler handler;
    @Autowired
    public MatchingConfig(MatchingHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(@NotNull WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/matching")
//                .addInterceptors(new HttpSessionHandshakeInterceptor())
//                .addInterceptors(new GlobalHandshakeInterceptor())
        ;
    }
}

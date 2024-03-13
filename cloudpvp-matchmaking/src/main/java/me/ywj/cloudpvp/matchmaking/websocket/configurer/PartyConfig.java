package me.ywj.cloudpvp.matchmaking.websocket.configurer;

import me.ywj.cloudpvp.matchmaking.websocket.handler.PartyHandler;
import me.ywj.cloudpvp.matchmaking.websocket.interceptor.GlobalHandshakeInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

/**
 * PartyConfig
 *
 * @author sheip9
 * @since 2024/3/8 15:24
 */
@Configuration
@EnableWebSocket
public class PartyConfig implements WebSocketConfigurer {
    PartyHandler partyHandler;
    @Autowired
    public PartyConfig(PartyHandler partyHandler) {
        this.partyHandler = partyHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(partyHandler, "/party")
                .addInterceptors(new HttpSessionHandshakeInterceptor())
                .addInterceptors(new GlobalHandshakeInterceptor())
        ;
    }
}

package me.ywj.cloudpvp.state.configurations;

import me.ywj.cloudpvp.state.websocket.StateSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebsocketConfiguration
 *
 * @author sheip9
 * @since 2024/10/16 17:50
 */
@Configuration
@EnableWebSocket
public class WebsocketConfiguration implements WebSocketConfigurer {
    private final StateSocketHandler stateSocketHandler;

    @Autowired
    public WebsocketConfiguration(StateSocketHandler stateSocketHandler) {
        this.stateSocketHandler = stateSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(stateSocketHandler, "/ws");
    }
}

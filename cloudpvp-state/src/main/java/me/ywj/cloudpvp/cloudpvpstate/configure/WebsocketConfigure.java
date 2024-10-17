package me.ywj.cloudpvp.cloudpvpstate.configure;

import me.ywj.cloudpvp.cloudpvpstate.websocket.StateSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebsocketConfigure
 *
 * @author sheip9
 * @since 2024/10/16 17:50
 */
@Configuration
@EnableWebSocket
public class WebsocketConfigure  implements WebSocketConfigurer {
    private final StateSocketHandler stateSocketHandler;
    @Autowired
    public WebsocketConfigure(StateSocketHandler stateSocketHandler) {
        this.stateSocketHandler = stateSocketHandler;
    }
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(stateSocketHandler, "/ws");
    }
}

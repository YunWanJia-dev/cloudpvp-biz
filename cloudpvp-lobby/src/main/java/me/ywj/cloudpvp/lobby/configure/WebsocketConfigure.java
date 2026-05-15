package me.ywj.cloudpvp.lobby.configure;

import me.ywj.cloudpvp.lobby.interceptor.IdInterceptor;
import me.ywj.cloudpvp.lobby.websocket.LobbySocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebsocketConfigure
 * 大厅长连接配置类
 *
 * @author sheip9
 * @since 2024/10/20 15:44
 */
@Configuration
@EnableWebSocket
public class WebsocketConfigure implements WebSocketConfigurer {
    private final LobbySocketHandler lobbySocketHandler;
    private final IdInterceptor idInterceptor;

    /**
     * WebsocketConfigure
     * 创建大厅 websocket 配置。
     *
     * @param lobbySocketHandler 大厅 websocket 处理器
     * @param idInterceptor 玩家ID握手拦截器
     */
    @Autowired
    public WebsocketConfigure(LobbySocketHandler lobbySocketHandler, IdInterceptor idInterceptor) {
        this.lobbySocketHandler = lobbySocketHandler;
        this.idInterceptor = idInterceptor;
    }

    /**
     * registerWebSocketHandlers
     * 注册大厅 websocket 路由和握手拦截器。
     *
     * @param registry websocket handler 注册表
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(lobbySocketHandler, LobbySocketHandler.PATH).addInterceptors(idInterceptor).setAllowedOrigins("*");
    }
}

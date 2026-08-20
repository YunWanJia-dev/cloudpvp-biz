package me.ywj.cloudpvp.lobby.configurations;

import me.ywj.cloudpvp.lobby.interceptor.IdInterceptor;
import me.ywj.cloudpvp.lobby.websocket.LobbySocketHandler;
import me.ywj.cloudpvp.lobby.websocket.MatchSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebsocketConfiguration
 * 大厅与比赛长连接配置类。
 *
 * @author sheip9
 * @since 2024/10/20 15:44
 */
@Configuration
@EnableWebSocket
public class WebsocketConfiguration implements WebSocketConfigurer {
    private final LobbySocketHandler lobbySocketHandler;
    private final MatchSocketHandler matchSocketHandler;
    private final IdInterceptor idInterceptor;

    /**
     * WebsocketConfiguration
     * 创建大厅与比赛 websocket 配置。
     *
     * @param lobbySocketHandler 大厅 websocket 处理器
     * @param matchSocketHandler 比赛 websocket 处理器
     * @param idInterceptor 玩家ID握手拦截器
     */
    @Autowired
    public WebsocketConfiguration(
            LobbySocketHandler lobbySocketHandler,
            MatchSocketHandler matchSocketHandler,
            IdInterceptor idInterceptor
    ) {
        this.lobbySocketHandler = lobbySocketHandler;
        this.matchSocketHandler = matchSocketHandler;
        this.idInterceptor = idInterceptor;
    }

    /**
     * registerWebSocketHandlers
     * 注册大厅与比赛 websocket 路由和握手拦截器。
     *
     * @param registry websocket handler 注册表
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(lobbySocketHandler, LobbySocketHandler.PATH).addInterceptors(idInterceptor).setAllowedOrigins("*");
        registry.addHandler(matchSocketHandler, MatchSocketHandler.PATH).addInterceptors(idInterceptor).setAllowedOrigins("*");
    }
}

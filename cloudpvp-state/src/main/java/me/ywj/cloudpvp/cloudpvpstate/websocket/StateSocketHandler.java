package me.ywj.cloudpvp.cloudpvpstate.websocket;

import me.ywj.cloudpvp.cloudpvpstate.entity.PlayerState;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.util.HashMap;

/**
 * StateSocketHandler
 *
 * @author sheip9
 * @since 2024/10/16 17:51
 */
public class StateSocketHandler extends AbstractWebSocketHandler implements WebSocketHandler {
    private static final HashMap<String, PlayerState> PLAYER_MAP = new HashMap<>();

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        super.handleMessage(session, message);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        super.afterConnectionEstablished(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        super.afterConnectionClosed(session, status);
    }
}

package me.ywj.cloudpvp.matchmaking.websocket.handler;

import jakarta.websocket.server.ServerEndpoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * MatchMakingHandler
 *
 * @author sheip9
 * @since 2024/2/7 16:55
 */
@Service
public class MatchMakingHandler extends AbstractWebSocketHandler {
    private static final ConcurrentLinkedDeque<WebSocketSession> SESSION_DEQUE = new ConcurrentLinkedDeque<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        SESSION_DEQUE.add(session);
    }
    @MessageMapping
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        super.handleTextMessage(session, message);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        SESSION_DEQUE.remove(session);
    }
}

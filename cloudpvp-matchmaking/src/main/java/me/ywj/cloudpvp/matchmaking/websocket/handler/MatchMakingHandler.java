package me.ywj.cloudpvp.matchmaking.websocket.handler;

import org.jetbrains.annotations.NotNull;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
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
    public void afterConnectionEstablished(@NotNull WebSocketSession session) throws Exception {
        SESSION_DEQUE.add(session);
    }
    @MessageMapping
    @Override
    protected void handleTextMessage(@NotNull WebSocketSession session, @NotNull TextMessage message) throws Exception {
        super.handleTextMessage(session, message);
    }

    @Override
    public void afterConnectionClosed(@NotNull WebSocketSession session, @NotNull CloseStatus status) throws Exception {
        SESSION_DEQUE.remove(session);
    }
}

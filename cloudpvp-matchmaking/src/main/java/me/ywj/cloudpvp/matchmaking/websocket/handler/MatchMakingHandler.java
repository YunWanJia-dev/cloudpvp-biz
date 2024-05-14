package me.ywj.cloudpvp.matchmaking.websocket.handler;

import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.*;

import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * MatchMakingHandler
 *
 * @author sheip9
 * @since 2024/2/7 16:55
 */
@Controller
public class MatchMakingHandler extends BaseMatchMakingHandler {
    private static final ConcurrentLinkedDeque<WebSocketSession> SESSION_DEQUE = new ConcurrentLinkedDeque<>();

    @Override
    public void afterConnectionEstablished(@NotNull WebSocketSession session) throws Exception {
        SESSION_DEQUE.add(session);
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {

    }

    @Override
    public void afterConnectionClosed(@NotNull WebSocketSession session, @NotNull CloseStatus status) throws Exception {
        SESSION_DEQUE.remove(session);
    }

}

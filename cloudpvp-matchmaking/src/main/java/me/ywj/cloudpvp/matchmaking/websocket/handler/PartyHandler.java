package me.ywj.cloudpvp.matchmaking.websocket.handler;

import me.ywj.cloudpvp.matchmaking.service.IPartyService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * PartyHandler
 *
 * @author sheip9
 * @since 2024/2/28 18:44
 */
public class PartyHandler implements WebSocketHandler {
    private static final ConcurrentLinkedDeque<WebSocketSession> SESSION_DEQUE = new ConcurrentLinkedDeque<>();

    IPartyService partyService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String playerId = (String) session.getAttributes().get("playerId");
        SESSION_DEQUE.add(session);
        partyService.joinParty(playerId, playerId);
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {

    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {

    }


    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        SESSION_DEQUE.remove(session);
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }
}

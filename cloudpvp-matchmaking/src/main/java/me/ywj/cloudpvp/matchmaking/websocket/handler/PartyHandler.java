package me.ywj.cloudpvp.matchmaking.websocket.handler;

import me.ywj.cloudpvp.matchmaking.service.IPartyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * PartyHandler
 *
 * @author sheip9
 * @since 2024/2/28 18:44
 */
@Controller
public class PartyHandler implements WebSocketHandler {
    private static final ConcurrentLinkedDeque<WebSocketSession> SESSION_DEQUE = new ConcurrentLinkedDeque<>();

    IPartyService partyService;
    @Autowired
    public PartyHandler(IPartyService partyService) {
        this.partyService = partyService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        System.out.println(session.getAttributes());

        String playerId = (String) session.getAttributes().get("playerId");


        SESSION_DEQUE.add(session);
        System.out.println(playerId);
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

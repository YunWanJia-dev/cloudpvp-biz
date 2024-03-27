package me.ywj.cloudpvp.matchmaking.websocket.handler;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import me.ywj.cloudpvp.matchmaking.entity.Player;
import me.ywj.cloudpvp.matchmaking.model.PartyPayload;
import me.ywj.cloudpvp.matchmaking.service.IPartyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.*;

import java.util.HashMap;
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
    private static final HashMap<String, Player> PLAYER_MAP = new HashMap<>();
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
        Player player = new Player(session, playerId);
        PLAYER_MAP.put(playerId, player);

        partyService.create(player);
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        String playerId = (String) session.getAttributes().get("playerId");

        PartyPayload payload;
        try {
            payload= JSON.parseObject(message.getPayload().toString(), PartyPayload.class);
        }catch (JSONException e){
            return;
        }
        System.out.println(payload);
        switch (payload.getAction()){
            case JOIN_PARTY -> partyService.join(PLAYER_MAP.get(playerId), payload.getContent());
            case QUIT -> partyService.create(PLAYER_MAP.get(playerId));
            default -> {

            }
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {

    }


    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String playerId = (String) session.getAttributes().get("playerId");
        partyService.disconnect(PLAYER_MAP.get(playerId));
        SESSION_DEQUE.remove(session);
        PLAYER_MAP.remove(playerId);
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }
}

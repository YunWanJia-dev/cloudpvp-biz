package me.ywj.cloudpvp.matchmaking.websocket.handler;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import me.ywj.cloudpvp.matchmaking.entity.Player;
import me.ywj.cloudpvp.matchmaking.model.PartyPayload;
import me.ywj.cloudpvp.matchmaking.service.IPartyService;
import org.jetbrains.annotations.NotNull;
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
    public void afterConnectionEstablished(@NotNull WebSocketSession session) throws Exception {
        System.out.println(session.getAttributes());
        String playerId = (String) session.getAttributes().get("playerId");


        SESSION_DEQUE.add(session);
        Player player = new Player(session, playerId);
        PLAYER_MAP.put(playerId, player);

        partyService.initStatus(player);
    }

    @Override
    public void handleMessage(@NotNull WebSocketSession session, @NotNull WebSocketMessage<?> message) throws Exception {
        String playerId = (String) session.getAttributes().get("playerId");

        PartyPayload payload;
        try {
            payload = JSON.parseObject(message.getPayload().toString(), PartyPayload.class);
        } catch (JSONException e) {
            return;
        }
        Player player = PLAYER_MAP.get(playerId);
        switch (payload.getAction()) {
            case JOIN_PARTY -> partyService.join(player, payload.getContent());
            case QUIT_PARTY -> partyService.join(player, playerId);
            case MESSAGE -> partyService.sendMessage(player, payload.getContent());
            default -> {
                
            }
        }
    }

    @Override
    public void handleTransportError(@NotNull WebSocketSession session, @NotNull Throwable exception) throws Exception {

    }


    @Override
    public void afterConnectionClosed(@NotNull WebSocketSession session, @NotNull CloseStatus status) throws Exception {
        String playerId = (String) session.getAttributes().get("playerId");
        partyService.disconnect(PLAYER_MAP.get(playerId));
        PLAYER_MAP.remove(playerId);
        SESSION_DEQUE.remove(session);
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }
}

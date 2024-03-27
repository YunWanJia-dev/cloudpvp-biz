package me.ywj.cloudpvp.matchmaking.entity;

import com.alibaba.fastjson2.JSON;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.annotation.Resource;
import lombok.Data;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

/**
 * Player
 *
 * @author sheip9
 * @since 2024/2/6 17:21
 */
@Data
public class Player {
    private String id;
    private String currentPartyId;

    @JsonIgnore
    private MsgListener listener;
    public Player(WebSocketSession session, String id) {
        listener = new MsgListener(session);
        this.id = id;
    }
}
class MsgListener implements MessageListener {
    WebSocketSession session;

    public MsgListener(WebSocketSession session) {
        this.session = session;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            session.sendMessage(new TextMessage(JSON.toJSONBytes(message.getBody())));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
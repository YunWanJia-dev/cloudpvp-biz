package me.ywj.cloudpvp.matchmaking.service.impl;

import com.alibaba.fastjson.JSON;
import jakarta.annotation.Resource;
import me.ywj.cloudpvp.matchmaking.entity.Player;
import me.ywj.cloudpvp.matchmaking.service.IPartyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Objects;

/**
 * PartyServiceImple
 *
 * @author sheip9
 * @since 2024/2/28 19:11
 */
@Service
public class PartyServiceImpl implements IPartyService {
    @Resource
    RedisMessageListenerContainer container;
    @Resource
    RedisTemplate<String, String> redisTemplate;
    @Resource(name="redisTemplate")
    private HashOperations<String, String, String> playerHashOperations;

    /**
     * PAYER_PARTY_HASH
     * 定义 存储 玩家id->当前所在队伍id 的映射关系的 hash键名
     */
    private static final String PLAYER_HASH = "player";

    @Resource(name="redisTemplate")
    private HashOperations<String, String, HashSet<String>> partyHashOperations;
    /**
     * PARTY_HASH
     * 定义 存储 队伍id->此队伍的玩家 的映射关系的 hash键名
     */
    private static final String PARTY_HASH = "party";
    @Resource
    private KafkaTemplate<String, String> kafkaTemplate;
    private void quitCurrentParty(String playerId) {
        String playerCurrentPartyId = playerHashOperations.get(PLAYER_HASH, playerId);
        if (Objects.isNull(playerCurrentPartyId)){
            return;
        }
        HashSet<String> set = partyHashOperations.get(PARTY_HASH, playerCurrentPartyId);
        if (Objects.isNull(set)){
            return;
        }
        set.remove(playerId);
        if (set.isEmpty()){
            partyHashOperations.delete(PARTY_HASH, playerCurrentPartyId);
            return;
        }
        partyHashOperations.putIfAbsent(PARTY_HASH, playerCurrentPartyId, set);
    }

    /**
     * playerUpdate
     * 更新玩家所在队伍
     * @param playerId 玩家id
     * @param partyId 队伍id
     */
    private void playerUpdate(String playerId, String partyId) {
        //如果队伍id等于玩家id，则为创建新队伍的操作
        HashSet<String> set = playerId.equals(partyId) ? new HashSet<>() : partyHashOperations.get(PARTY_HASH, partyId);
        if (Objects.isNull(set)) {
            //若队伍不存在，则停止下一步的操作
            return;
        }
        quitCurrentParty(playerId);
        set.add(playerId);
        playerHashOperations.put(PLAYER_HASH, playerId, partyId);
        partyHashOperations.put(PARTY_HASH, partyId, set);
    }
    @Override
    public void create(Player player){
        player.setCurrentPartyId(player.getId());
        playerUpdate(player.getId(), player.getCurrentPartyId());
        container.addMessageListener(player.getListener(), new PatternTopic(player.getCurrentPartyId()));
        redisTemplate.convertAndSend(player.getCurrentPartyId(), "Hi!");
    }
    @Override
    public void join(Player player, String partyId) {
        redisTemplate.convertAndSend(player.getCurrentPartyId(), "player" + player.getId() + "is quited.");
        container.removeMessageListener(player.getListener(), new PatternTopic(player.getCurrentPartyId()));
        player.setCurrentPartyId(partyId);
        playerUpdate(player.getId(), partyId);
        container.addMessageListener(player.getListener(), new PatternTopic(player.getCurrentPartyId()));
        redisTemplate.convertAndSend(partyId, "player" + player.getId() + "is joined.");
    }

    @Override
    public void disconnect(Player player) {
        quitCurrentParty(player.getId());
        redisTemplate.convertAndSend(player.getCurrentPartyId(), "player" + player.getId() + "is quited.");
        playerHashOperations.delete(PLAYER_HASH, player.getId());
    }
}
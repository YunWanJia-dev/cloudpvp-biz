package me.ywj.cloudpvp.matchmaking.service.impl;

import jakarta.annotation.Resource;
import me.ywj.cloudpvp.matchmaking.entity.Player;
import me.ywj.cloudpvp.matchmaking.model.PartyMessage;
import me.ywj.cloudpvp.matchmaking.service.IPartyService;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Objects;

/**
 * PartyServiceImpl
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
    private void redisRemoveFromParty(String playerId) {
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
     * 更新Redis中玩家所在队伍
     * @param playerId 玩家id
     * @param partyId 队伍id
     */
    private void redisUpdate(@NotNull String playerId, String partyId) {
        //如果队伍id等于玩家id，则为创建新队伍的操作
        HashSet<String> set = playerId.equals(partyId) ? new HashSet<>() : partyHashOperations.get(PARTY_HASH, partyId);
        if (Objects.isNull(set)) {
            //若队伍不存在，则停止下一步的操作
            return;
        }
        redisRemoveFromParty(playerId);
        set.add(playerId);
        playerHashOperations.put(PLAYER_HASH, playerId, partyId);
        partyHashOperations.put(PARTY_HASH, partyId, set);
    }
    @Override
    public void initStatus(@NotNull Player player){
        player.setCurrentPartyId(player.getId());
        redisUpdate(player.getId(), player.getCurrentPartyId());
        container.addMessageListener(player.getListener(), new PatternTopic(player.getCurrentPartyId()));
//        redisTemplate.convertAndSend(player.getCurrentPartyId(), "Hi!");
    }
    @Override
    public void join(@NotNull Player player, String partyId) {
        //TODO: 队伍房主退出加入其他房间时的操作
        redisTemplate.convertAndSend(player.getCurrentPartyId(), PartyMessage.playerQuit(player.getId()));
        container.removeMessageListener(player.getListener(), new PatternTopic(player.getCurrentPartyId()));
        player.setCurrentPartyId(partyId);
        redisUpdate(player.getId(), partyId);
        container.addMessageListener(player.getListener(), new PatternTopic(partyId));
        redisTemplate.convertAndSend(partyId, PartyMessage.playerJoin(player.getId()));
    }

    @Override
    public void disconnect(@NotNull Player player) {
        redisRemoveFromParty(player.getId());
        redisTemplate.convertAndSend(player.getCurrentPartyId(), PartyMessage.playerJoin(player.getId()));
        playerHashOperations.delete(PLAYER_HASH, player.getId());
    }
}
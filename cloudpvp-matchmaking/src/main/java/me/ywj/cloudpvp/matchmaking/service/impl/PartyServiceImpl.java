package me.ywj.cloudpvp.matchmaking.service.impl;

import jakarta.annotation.Resource;
import me.ywj.cloudpvp.matchmaking.service.IPartyService;
import org.springframework.data.redis.core.HashOperations;
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
    public void create(String playerId){
        playerUpdate(playerId, playerId);
    }
    @Override
    public void join(String playerId, String partyId) {
        playerUpdate(playerId, partyId);
    }

    @Override
    public void disconnect(String playerId) {
        quitCurrentParty(playerId);
        playerHashOperations.delete(PLAYER_HASH, playerId);
    }
}
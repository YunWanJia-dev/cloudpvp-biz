package me.ywj.cloudpvp.matchmaking.service.impl;

import jakarta.annotation.Resource;
import me.ywj.cloudpvp.matchmaking.entity.Party;
import me.ywj.cloudpvp.matchmaking.service.IPartyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * PartyServiceImple
 *
 * @author sheip9
 * @since 2024/2/28 19:11
 */
@Service
public class PartyServiceImpl implements IPartyService {
    RedisTemplate<String, String> template;

    @Autowired
    public PartyServiceImpl(RedisTemplate<String, String> template) {
        this.template = template;
    }

    @Resource(name="redisTemplate")
    private ListOperations<String, String> listOps;
    @Override
    public void joinParty(String playerId, String partyId) {
        listOps.leftPush(playerId, partyId);
    }

    @Override
    public void playerDisconnect(String playerId) {

    }
}

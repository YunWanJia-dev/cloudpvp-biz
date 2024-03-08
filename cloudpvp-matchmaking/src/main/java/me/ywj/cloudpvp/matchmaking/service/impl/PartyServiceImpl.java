package me.ywj.cloudpvp.matchmaking.service.impl;

import jakarta.annotation.Resource;
import me.ywj.cloudpvp.matchmaking.entity.Party;
import me.ywj.cloudpvp.matchmaking.service.IPartyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * PartyServiceImple
 *
 * @author sheip9
 * @since 2024/2/28 19:11
 */
public class PartyServiceImpl implements IPartyService {
    RedisTemplate<String, Party> template;

    @Autowired
    public PartyServiceImpl(RedisTemplate<String, Party> template) {
        this.template = template;
    }

    @Resource(name="redisTemplate")
    private ListOperations<String, Party> listOps;
    @Override
    public void joinParty(String playerId, String partyId) {

    }

    @Override
    public void playerDisconnect(String playerId) {

    }
}

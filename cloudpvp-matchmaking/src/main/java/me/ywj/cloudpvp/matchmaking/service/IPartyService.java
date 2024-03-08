package me.ywj.cloudpvp.matchmaking.service;

import org.springframework.stereotype.Service;

/**
 * IPartyService
 *
 * @author sheip9
 * @since 2024/2/28 19:10
 */
@Service
public interface IPartyService {
    /**
     * JoinParty
     * 玩家加入队伍
     * @param playerId 玩家id
     * @param partyId 队伍id
     */
    void joinParty(String playerId, String partyId);

    /**
     * playerDisconnect
     * 玩家断开连接
     * @param playerId 玩家id
     */
    void playerDisconnect(String playerId);
}

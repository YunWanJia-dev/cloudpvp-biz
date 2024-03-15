package me.ywj.cloudpvp.matchmaking.service;

/**
 * IPartyService
 *
 * @author sheip9
 * @since 2024/2/28 19:10
 */

public interface IPartyService {
    /**
     * create
     * 玩家创建队伍
     * @param playerId
     */
    void create(String playerId);
    /**
     * join
     * 玩家加入队伍
     * @param playerId 玩家id
     * @param partyId 队伍id
     */
    void join(String playerId, String partyId);

    /**
     * disconnect
     * 玩家断开连接
     * @param playerId 玩家id
     */
    void disconnect(String playerId);
}

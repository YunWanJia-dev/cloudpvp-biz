package me.ywj.cloudpvp.matchmaking.service;

import me.ywj.cloudpvp.matchmaking.entity.Player;

/**
 * IPartyService
 *
 * @author sheip9
 * @since 2024/2/28 19:10
 */

public interface IPartyService {
    /**
     * initStatus
     * 玩家连接，初始化状态
     * @param player 玩家实体
     */
    void initStatus(Player player);
    /**
     * join
     * 玩家加入队伍
     * @param player 玩家实体
     * @param partyId 队伍id
     */
    void join(Player player, String partyId);

    /**
     * disconnect
     * 玩家断开连接
     * @param player 玩家id
     */
    void disconnect(Player player);

    /**
     * sendMessage
     * 玩家聊天发送消息
     * @param player 玩家id
     */
    void sendMessage(Player player, String content);
}

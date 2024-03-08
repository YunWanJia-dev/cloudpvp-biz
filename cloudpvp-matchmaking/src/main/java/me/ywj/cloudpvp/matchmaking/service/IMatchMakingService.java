package me.ywj.cloudpvp.matchmaking.service;

/**
 * IMatchMakingService
 *
 * @author sheip9
 * @since 2024/3/8 15:04
 */
public interface IMatchMakingService {
    /**
     * startMatchMaking
     * 将某一队伍放入匹配队列
     * @param partyId 队伍id
     */
    void startMatchMaking(String partyId);

    /**
     * stopMatchMaking
     * 将某一队伍移除匹配队列
     * @param partyId
     */
    void stopMatchMaking(String partyId);
}

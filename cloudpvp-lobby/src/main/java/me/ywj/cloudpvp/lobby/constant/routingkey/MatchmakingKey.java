package me.ywj.cloudpvp.lobby.constant.routingkey;

/**
 * MatchmakingKey
 * Matchmaking 的 MQ 路由键。
 *
 * @author sheip9
 * @since 2026/6/16
 **/
public enum MatchmakingKey {
    Enqueue("lobby.matchmaking.enqueue"),
    Cancel("matchmaking.cancel"),
    ;

    public final String routingKey;

    MatchmakingKey(String routingKey) {
        this.routingKey = routingKey;
    }
}

package me.ywj.cloudpvp.lobby.constant.queue;

/**
 * MatchmakingQueue
 * Matchmaking 的 MQ 队列声明。
 *
 * @author sheip9
 * @since 2026/6/16 13:54
 */
public enum MatchmakingQueue {
    Submit("matchmaking.submit.queue"),
    Cancel("matchmaking.cancel.queue"),
    ;

    public final String queueName;

    MatchmakingQueue(String queueName) {
        this.queueName = queueName;
    }
}

package me.ywj.cloudpvp.lobby.constant.queue;

/**
 * MatchmakingQueue
 * Matchmaking 的 MQ 队列声明。
 *
 * @author sheip9
 * @since 2026/6/16 13:54
 */
public enum MatchmakingQueue {
    // 本模块发送：匹配请求与取消消息通过交换机路由至以下队列，由 Matcher 消费。
    Request("matcher.lobby.enqueue"),
    Cancel("matcher.lobby.cancel"),

    // 本模块监听：接收 Matcher/Allocator 回传的大厅匹配状态与比赛生命周期消息。
    Lobby("lobby.lobby.update"),
    MatchBiz("lobby.match.update"),
    ;

    public final String queueName;

    MatchmakingQueue(String queueName) {
        this.queueName = queueName;
    }
}

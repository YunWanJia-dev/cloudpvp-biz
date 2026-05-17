package me.ywj.cloudpvp.lobby.exceptions;

import me.ywj.cloudpvp.core.exceptions.BizException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * PlayerAlreadyInLobbyException
 * 玩家已经属于其他大厅。
 *
 * @author sheip9
 * @since 2026/5/17 17:27
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class PlayerAlreadyInLobbyException extends BizException {
    /**
     * 构造玩家已在大厅异常。
     *
     * @param playerId 已经加入大厅的玩家 ID
     * @param lobbyId 玩家当前所在大厅 ID
     */
    public PlayerAlreadyInLobbyException(Number playerId, Number lobbyId) {
        super("Player " + playerId + " is already in lobby " + lobbyId);
    }
}

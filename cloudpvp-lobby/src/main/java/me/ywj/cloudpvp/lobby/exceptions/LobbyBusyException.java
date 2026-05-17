package me.ywj.cloudpvp.lobby.exceptions;

import me.ywj.cloudpvp.core.exceptions.BizException;
import me.ywj.cloudpvp.core.model.base.ErrorType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * LobbyBusyException
 * 大厅正在处理并发操作。
 *
 * @author sheip9
 * @since 2026/5/16 16:57
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class LobbyBusyException extends BizException implements LobbySocketError {
    /**
     * 构造指定大厅繁忙异常。
     *
     * @param lobbyId 正在被并发操作占用的大厅 ID
     */
    public LobbyBusyException(Number lobbyId) {
        super("Lobby " + lobbyId + " is busy");
    }

    /**
     * 构造自定义繁忙异常。
     *
     * @param message 异常说明
     */
    public LobbyBusyException(String message) {
        super(message);
    }

    /**
     * 返回大厅繁忙对应的 WebSocket 错误类型。
     *
     * @return 大厅繁忙错误类型
     */
    @Override
    public ErrorType getErrorType() {
        return ErrorType.LOBBY_BUSY;
    }
}

package me.ywj.cloudpvp.lobby.exceptions;

import me.ywj.cloudpvp.core.exceptions.BizException;
import me.ywj.cloudpvp.core.model.base.ErrorType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * LobbyNotExist
 *
 * @author sheip9
 * @since 2024/10/20 20:43
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class LobbyNotExist extends BizException implements LobbySocketError {
    public LobbyNotExist() {
        super("Lobby does not exist");
    }

    public LobbyNotExist(String message) {
        super(message);
    }

    /**
     * 返回大厅不存在对应的 WebSocket 错误类型。
     *
     * @return 大厅不存在错误类型
     */
    @Override
    public ErrorType getErrorType() {
        return ErrorType.LOBBY_NOT_EXIST;
    }
}

package me.ywj.cloudpvp.lobby.exceptions;

import me.ywj.cloudpvp.core.exceptions.BizException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * PlayerNotInLobbyException
 * 玩家当前不在任何大厅，无法执行相关操作。
 *
 * @author sheip9
 * @since 2026/8/19 22:05
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class PlayerNotInLobbyException extends BizException {
}

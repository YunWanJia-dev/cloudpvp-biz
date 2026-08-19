package me.ywj.cloudpvp.lobby.exceptions;

import me.ywj.cloudpvp.core.exceptions.BizException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * PlayerStateIllegalException
 * 玩家状态存在异常。
 *
 * @author sheip9
 * @since 2026/8/19 22:05
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class PlayerStateIllegalException extends BizException {
}

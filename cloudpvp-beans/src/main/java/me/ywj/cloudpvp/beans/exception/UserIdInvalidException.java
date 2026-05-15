package me.ywj.cloudpvp.beans.exception;

import me.ywj.cloudpvp.core.exception.BizException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * UserIdInvalidException
 * 用户id无效异常
 *
 * @author sheip9
 * @since 2024/10/19 00:13
 */
@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class UserIdInvalidException extends BizException {
    public UserIdInvalidException() {
        super("用户id无效");
    }
}

package me.ywj.cloudpvp.core.exception;

/**
 * BusinessException
 * 业务异常
 *
 * @author sheip9
 * @since 2024/10/24 17:14
 */
public abstract class BizException extends RuntimeException {
    public BizException() {
        super();
    }

    public BizException(String message) {
        super(message);
    }

    @Override
    public synchronized Throwable fillInStackTrace(){
        return this;
    }
}

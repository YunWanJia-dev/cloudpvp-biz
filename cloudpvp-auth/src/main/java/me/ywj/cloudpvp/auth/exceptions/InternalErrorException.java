package me.ywj.cloudpvp.auth.exceptions;

/**
 * InternalErrorException
 *
 * @author sheip9
 * @since 2024/1/21 11:47
 */
public class InternalErrorException extends RuntimeException{
    public InternalErrorException() {
        super();
    }

    public InternalErrorException(String message) {
        super(message);
    }
}

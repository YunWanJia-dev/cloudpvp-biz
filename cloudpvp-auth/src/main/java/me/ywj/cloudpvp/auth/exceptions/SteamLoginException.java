package me.ywj.cloudpvp.auth.exceptions;

/**
 * SteamLoginErrorException
 *
 * @author sheip9
 * @since 2026/5/15 10:34
 */
public class SteamLoginException extends RuntimeException {
    public SteamLoginException() {
        super();
    }

    public SteamLoginException(String message) {
        super(message);
    }

    public SteamLoginException(String message, Throwable cause) {
        super(message, cause);
    }
}

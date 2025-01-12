package me.ywj.cloudpvp.lobby.exception;

/**
 * LobbyNotExist
 *
 * @author sheip9
 * @since 2024/10/20 20:43
 */
public class LobbyNotExist extends RuntimeException {
    public LobbyNotExist() {
        super();
    }

    public LobbyNotExist(String message) {
        super(message);
    }
}

package me.ywj.cloudpvp.lobby.exceptions;

import me.ywj.cloudpvp.core.exceptions.BizException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * LobbyNotExist
 *
 * @author sheip9
 * @since 2024/10/20 20:43
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class LobbyNotExist extends BizException {
    public LobbyNotExist() {
        super("Lobby does not exist");
    }

    public LobbyNotExist(String message) {
        super(message);
    }
}

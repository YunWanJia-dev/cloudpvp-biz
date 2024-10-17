package me.ywj.cloudpvp.cloudpvpstate.service;

import me.ywj.cloudpvp.cloudpvpstate.entity.PlayerState;
import me.ywj.cloudpvp.cloudpvpstate.repository.PlayerStateRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * PlayerStateService
 *
 * @author sheip9
 * @since 2024/10/17 12:12
 */
@Service
public class PlayerStateService {
    private final PlayerStateRepository playerStateRepository;
    @Autowired
    public PlayerStateService(PlayerStateRepository playerStateRepository) {
        this.playerStateRepository = playerStateRepository;
    }
    public void setState(PlayerState playerState) {
        playerStateRepository.save(playerState);
    }
    public void onDisconnect(PlayerState playerState) {
        playerStateRepository.deleteById(playerState.getSteamId());
    }
}

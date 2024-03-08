package me.ywj.cloudpvp.matchmaking.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * Lobby
 *
 * @author sheip9
 * @since 2024/2/7 16:40
 */
@Data
@AllArgsConstructor
public class Party {
    private Player host;
    private List<Player> participates;
}

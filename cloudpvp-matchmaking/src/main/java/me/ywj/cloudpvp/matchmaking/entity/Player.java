package me.ywj.cloudpvp.matchmaking.entity;

import lombok.Data;

/**
 * Player
 *
 * @author sheip9
 * @since 2024/2/6 17:21
 */
@Data
public class Player {
    private String id;
    private Party currrentParty;
}

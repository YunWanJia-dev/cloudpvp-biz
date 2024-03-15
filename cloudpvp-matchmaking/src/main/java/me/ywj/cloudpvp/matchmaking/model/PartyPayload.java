package me.ywj.cloudpvp.matchmaking.model;

import lombok.Data;
import me.ywj.cloudpvp.matchmaking.constants.PartyActionEnum;

/**
 * PartyMessage
 *
 * @author sheip9
 * @since 2024/3/15 14:34
 */
@Data
public class PartyPayload {
    PartyActionEnum action;
    String content;
}

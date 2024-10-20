package me.ywj.cloudpvp.usersummary.service;

import me.ywj.cloudpvp.core.entity.PlayerProfile;
import me.ywj.cloudpvp.core.type.SteamIdKt;
import org.springframework.stereotype.Service;

/**
 * ProfileService
 *
 * @author sheip9
 * @since 2024/10/16 12:12
 */
@Service
public class ProfileService {
    public PlayerProfile getProfile(SteamIdKt id) {
        return null;
    } 
    public void requestUpdateProfile(SteamIdKt id) {
        //TODO: requesting via MQ
    }
}

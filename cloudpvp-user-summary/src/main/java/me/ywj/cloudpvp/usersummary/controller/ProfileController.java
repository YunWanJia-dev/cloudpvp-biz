package me.ywj.cloudpvp.usersummary.controller;

import me.ywj.cloudpvp.core.entity.PlayerProfile;
import me.ywj.cloudpvp.usersummary.constant.ProfileConstant;
import me.ywj.cloudpvp.usersummary.service.ProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/**
 * ProfileController
 *
 * @author sheip9
 * @since 2024/10/16 11:54
 */
@RestController
@RequestMapping("/profile")
public class ProfileController {
    final ProfileService profileService;
    
    @Autowired
    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("/{id}")
    public PlayerProfile getProfile(@PathVariable String id) {
        final PlayerProfile profile = profileService.getProfile(id);
        return Objects.isNull(profile) ? 
                profile : 
                new PlayerProfile(id, "用户" + id.substring(id.length() - 6, id.length() - 1), ProfileConstant.emptyAvatar);
    }
}

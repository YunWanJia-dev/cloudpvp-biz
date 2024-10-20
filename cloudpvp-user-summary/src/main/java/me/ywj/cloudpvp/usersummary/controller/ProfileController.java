package me.ywj.cloudpvp.usersummary.controller;

import me.ywj.cloudpvp.core.entity.PlayerProfile;
import me.ywj.cloudpvp.usersummary.service.ProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

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

    @GetMapping
    public List<PlayerProfile> getProfile(@RequestParam ArrayList<Long> ids) {
        return profileService.getProfile(ids);
    }
}

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
    private final ProfileService profileService;

    @Autowired
    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    /**
     * 获取多个用户的资料
     * @param ids 储存id的数组
     */
    @GetMapping
    public List<PlayerProfile> getProfile(@RequestParam ArrayList<Long> ids) {
        return profileService.getProfiles(ids);
    }

    /**
     * 获取自己的个人资料
     */
    @GetMapping("/self")
    public PlayerProfile getSelf() {
        //TODO: 从token里获取id
        return profileService.getOneProfile(0);
    }

    /**
     * 获取单个用户的个人资料
     * @param id 64位ID
     */
    @GetMapping("/{id}")
    public PlayerProfile getProfile(@PathVariable long id) {
        return profileService.getOneProfile(id);
    }
}

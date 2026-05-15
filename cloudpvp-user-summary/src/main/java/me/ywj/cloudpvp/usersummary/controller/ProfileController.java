package me.ywj.cloudpvp.usersummary.controller;

import lombok.AllArgsConstructor;
import me.ywj.cloudpvp.beans.exception.UserIdInvalidException;
import me.ywj.cloudpvp.beans.utils.TokenAuthUtils;
import me.ywj.cloudpvp.core.entity.PlayerProfile;
import me.ywj.cloudpvp.usersummary.service.ProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
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
@AllArgsConstructor(onConstructor = @__(@Autowired))
public class ProfileController {
    private final ProfileService profileService;
    private final TokenAuthUtils tokenAuthUtils;

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
     *
     * @param token 请求头中的 token
     */
    @GetMapping("/self")
    public PlayerProfile getSelf(@RequestHeader(HttpHeaders.AUTHORIZATION) String token) throws UserIdInvalidException {
        Long id = tokenAuthUtils.getIDFromToken(token);
        return profileService.getOneProfile(id);
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

package me.ywj.cloudpvp.state.controller

import me.ywj.cloudpvp.core.type.SteamID64
import me.ywj.cloudpvp.state.entity.PlayerState
import me.ywj.cloudpvp.state.service.PlayerStateService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * StateController
 *
 * @author sheip9
 * @since 2024/10/17 13:36
 */
@RestController
class StateController @Autowired constructor(val stateService: PlayerStateService) {
    @GetMapping
    fun getPlayerStates(@RequestParam ids: ArrayList<SteamID64>): Iterable<PlayerState?>? {
        return stateService.getStates(ids)
    }
}
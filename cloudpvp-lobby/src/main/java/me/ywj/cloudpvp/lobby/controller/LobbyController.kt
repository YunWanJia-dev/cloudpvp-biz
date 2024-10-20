package me.ywj.cloudpvp.lobby.controller

import me.ywj.cloudpvp.core.model.base.CreatedResponse
import me.ywj.cloudpvp.lobby.service.LobbyService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * LobbyController
 *
 * @author sheip9
 * @since 2024/10/20 15:47
 */
@RestController
@RequestMapping
class LobbyController @Autowired constructor(val lobbyService: LobbyService) {
    @PostMapping
    fun createLobby(): CreatedResponse {
        return CreatedResponse(lobbyService.createLobby())
    }
}
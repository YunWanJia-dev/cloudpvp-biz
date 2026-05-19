package me.ywj.cloudpvp.lobby.controller

import me.ywj.cloudpvp.beans.utils.TokenAuthUtils
import me.ywj.cloudpvp.core.model.base.CreatedResponse
import me.ywj.cloudpvp.lobby.model.SelectModeDTO
import me.ywj.cloudpvp.lobby.entity.Lobby
import me.ywj.cloudpvp.lobby.service.LobbyLifecycleService
import me.ywj.cloudpvp.lobby.service.LobbyMatchService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * LobbyController
 *
 * @author sheip9
 * @since 2024/10/20 15:47
 */
@RestController
@RequestMapping
class LobbyController @Autowired constructor(
    val lobbyLifecycleService: LobbyLifecycleService,
    val lobbyMatchService: LobbyMatchService,
    val tokenAuthUtils: TokenAuthUtils,
) {
    /**
     * 创建新大厅。
     */
    @PostMapping
    suspend fun createLobby(@RequestHeader(HttpHeaders.AUTHORIZATION) token: String): CreatedResponse {
        val playerId = tokenAuthUtils.getIDFromToken(token)
        return CreatedResponse(lobbyLifecycleService.createLobby(playerId))
    }

    /**
     * 查询当前玩家所在的大厅。
     */
    @GetMapping("/players/self/lobby")
    suspend fun getCurrentLobby(@RequestHeader(HttpHeaders.AUTHORIZATION) token: String): Lobby? {
        val playerId = tokenAuthUtils.getIDFromToken(token)
        return lobbyLifecycleService.getCurrentLobby(playerId)
    }

    /**
     * 通过 HTTP 将当前玩家加入指定大厅。
     */
    @PostMapping("/{lobbyId}/players/self")
    suspend fun joinLobby(
        @RequestHeader(HttpHeaders.AUTHORIZATION) token: String,
        @PathVariable lobbyId: Int,
    ): Lobby {
        val playerId = tokenAuthUtils.getIDFromToken(token)
        return lobbyLifecycleService.joinLobby(playerId, lobbyId)
    }

    /**
     * 通过 HTTP 将当前玩家从所在大厅移除。
     */
    @DeleteMapping("/players/self")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun leaveLobby(
        @RequestHeader(HttpHeaders.AUTHORIZATION) token: String,
    ) {
        val playerId = tokenAuthUtils.getIDFromToken(token)
        lobbyLifecycleService.leaveLobby(playerId)
    }

    /**
     * 选择游戏模式。只有房主可在 WAITING 状态下修改。
     */
    @PatchMapping("/{lobbyId}/mode")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun selectMode(
        @RequestHeader(HttpHeaders.AUTHORIZATION) token: String,
        @PathVariable lobbyId: Int,
        @RequestBody body: SelectModeDTO,
    ) {
        val playerId = tokenAuthUtils.getIDFromToken(token)
        lobbyMatchService.selectMode(lobbyId, playerId, body)
    }

    /**
     * 开始匹配。只有房主可操作。
     */
    @PostMapping("/{lobbyId}/match/start")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun startMatching(
        @RequestHeader(HttpHeaders.AUTHORIZATION) token: String,
        @PathVariable lobbyId: Int,
    ) {
        val playerId = tokenAuthUtils.getIDFromToken(token)
        lobbyMatchService.startMatching(lobbyId, playerId)
    }

    /**
     * 停止匹配。只有房主可操作。
     */
    @PostMapping("/{lobbyId}/match/stop")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun stopMatching(
        @RequestHeader(HttpHeaders.AUTHORIZATION) token: String,
        @PathVariable lobbyId: Int,
    ) {
        val playerId = tokenAuthUtils.getIDFromToken(token)
        lobbyMatchService.stopMatching(lobbyId, playerId)
    }

    /**
     * 确认比赛。
     */
    @PostMapping("/{lobbyId}/match/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun confirmMatch(
        @RequestHeader(HttpHeaders.AUTHORIZATION) token: String,
        @PathVariable lobbyId: Int,
    ) {
        val playerId = tokenAuthUtils.getIDFromToken(token)
        lobbyMatchService.confirmMatch(lobbyId, playerId)
    }

    /**
     * 通过 HTTP 向当前玩家所在大厅发送文本消息。
     */
    @PostMapping("/messages/text")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun sendTextMessage(
        @RequestHeader(HttpHeaders.AUTHORIZATION) token: String,
        @RequestBody body: Map<String, String>,
    ) {
        val playerId = tokenAuthUtils.getIDFromToken(token)
        lobbyLifecycleService.sendTextMessage(playerId, body["content"].orEmpty())
    }
}

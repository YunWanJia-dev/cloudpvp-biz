package me.ywj.cloudpvp.lobby.controller

import me.ywj.cloudpvp.beans.utils.TokenAuthUtils
import me.ywj.cloudpvp.core.model.base.CreatedResponse
import me.ywj.cloudpvp.lobby.entity.Lobby
import me.ywj.cloudpvp.lobby.service.LobbyService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
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
    val lobbyService: LobbyService,
    val tokenAuthUtils: TokenAuthUtils,
) {
    /**
     * 创建新大厅。
     *
     * @return 创建成功后的大厅 ID 响应
     */
    @PostMapping
    suspend fun createLobby(@RequestHeader(HttpHeaders.AUTHORIZATION) token: String,): CreatedResponse {
        val playerId = tokenAuthUtils.getIDFromToken(token)
        return CreatedResponse(lobbyService.createLobby(playerId))
    }

    /**
     * 通过 HTTP 将当前玩家加入指定大厅。
     *
     * @param token 请求头中的授权令牌
     * @param lobbyId 目标大厅 ID
     * @return 加入后的玩家 ID 列表
     */
    @PostMapping("/{lobbyId}/players/self")
    suspend fun joinLobby(
        @RequestHeader(HttpHeaders.AUTHORIZATION) token: String,
        @PathVariable lobbyId: Int,
    ): Lobby? {
        val playerId = tokenAuthUtils.getIDFromToken(token)
        return lobbyService.joinLobby(playerId, lobbyId)
    }

    /**
     * 通过 HTTP 将当前玩家从所在大厅移除。
     *
     * @param token 请求头中的授权令牌
     */
    @DeleteMapping("/players/self")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun leaveLobby(
        @RequestHeader(HttpHeaders.AUTHORIZATION) token: String,
    ) {
        val playerId = tokenAuthUtils.getIDFromToken(token)
        lobbyService.leaveLobby(playerId)
    }

    /**
     * 通过 HTTP 向当前玩家所在大厅发送文本消息。
     *
     * @param token 请求头中的授权令牌
     * @param body 请求体，使用 content 字段传递文本内容
     */
    @PostMapping("/messages/text")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun sendTextMessage(
        @RequestHeader(HttpHeaders.AUTHORIZATION) token: String,
        @RequestBody body: Map<String, String>,
    ) {
        val playerId = tokenAuthUtils.getIDFromToken(token)
        lobbyService.sendTextMessage(playerId, body["content"].orEmpty())
    }
}

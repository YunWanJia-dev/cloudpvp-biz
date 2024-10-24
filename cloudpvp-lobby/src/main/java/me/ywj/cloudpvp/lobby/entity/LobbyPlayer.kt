package me.ywj.cloudpvp.lobby.entity

import me.ywj.cloudpvp.core.entity.BasicPlayer
import me.ywj.cloudpvp.core.type.LobbyId
import me.ywj.cloudpvp.core.type.SteamId64
import me.ywj.cloudpvp.lobby.websocket.LobbySocketHandler
import me.ywj.cloudpvp.lobby.websocket.LobbySocketHandler.Companion.PARAM_LOBBY_ID
import org.springframework.data.annotation.Id
import org.springframework.data.redis.core.RedisHash
import org.springframework.web.socket.WebSocketSession

/**
 * LobbyPlayer
 *
 * @author sheip9
 * @since 2024/10/20 16:31
 */
@RedisHash("LobbyPlayer")
class LobbyPlayer(@Id override val steamId64 : SteamId64, val session : WebSocketSession) : BasicPlayer(steamId64)  {
    var lobbyId : LobbyId = session.getRequestLobbyId()
}
fun WebSocketSession.getRequestLobbyId(): Int {
    return( LobbySocketHandler.URI_TEMPLATE.match(uri!!.path)[PARAM_LOBBY_ID] as String).toInt()
}
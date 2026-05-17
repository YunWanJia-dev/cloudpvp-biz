package me.ywj.cloudpvp.lobby.entity

import me.ywj.cloudpvp.core.type.LobbyId
import me.ywj.cloudpvp.core.type.SteamID64
import org.springframework.data.annotation.Id
import org.springframework.data.redis.core.RedisHash

/**
 * PlayerLobby
 * 玩家当前所在大厅索引。
 *
 * @author sheip9
 * @since 2026/5/17 17:27
 */
@RedisHash("PlayerLobby")
data class PlayerLobby(
    @Id val playerId: SteamID64,
    var lobbyId: LobbyId,
)

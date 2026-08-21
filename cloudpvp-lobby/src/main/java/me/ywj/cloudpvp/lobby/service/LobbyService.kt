package me.ywj.cloudpvp.lobby.service

import kotlinx.coroutines.*
import me.ywj.cloudpvp.core.model.lobby.LobbyStatus
import me.ywj.cloudpvp.core.type.LobbyId
import me.ywj.cloudpvp.core.type.SteamID64
import me.ywj.cloudpvp.core.utils.LobbyUtils
import me.ywj.cloudpvp.lobby.entity.Lobby
import me.ywj.cloudpvp.lobby.entity.PlayerLobby
import me.ywj.cloudpvp.lobby.exceptions.LobbyBusyException
import me.ywj.cloudpvp.lobby.exceptions.LobbyNotExist
import me.ywj.cloudpvp.lobby.exceptions.PlayerAlreadyInLobbyException
import me.ywj.cloudpvp.lobby.exceptions.PlayerStateIllegalException
import me.ywj.cloudpvp.lobby.model.publishing.LobbyMessage
import me.ywj.cloudpvp.lobby.model.publishing.LobbyMessageType
import me.ywj.cloudpvp.lobby.repository.LobbyRepository
import me.ywj.cloudpvp.lobby.repository.PlayerLobbyRepository
import me.ywj.cloudpvp.lobby.utils.RedisLockUtils.withPlayerAndLobbyLock
import org.redisson.api.RedissonClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service

/**
 * LobbyService
 * 大厅生命周期管理：创建、加入、退出、订阅、文本消息等基础操作。
 *
 * @author sheip9
 * @since 2024/10/20 16:35
 */
@Service
class LobbyService @Autowired constructor(
    val lobbyRepository: LobbyRepository,
    val playerLobbyRepository: PlayerLobbyRepository,
    val redisTemplate: RedisTemplate<String, Any>,
    val redissonClient: RedissonClient,
) {
    companion object {
        private const val CREATE_LOBBY_ATTEMPTS = 8
        private val publishingScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    /**
     * 创建大厅，将创建者加入大厅并返回大厅 ID。
     *
     * @return 新创建大厅的 ID
     * @throws LobbyBusyException 当多次生成 ID 后仍无法获得锁或完成创建时抛出
     * @throws PlayerAlreadyInLobbyException 当玩家已属于其他大厅时抛出
     */
    suspend fun createLobby(playerId: SteamID64): LobbyId {
        repeat(CREATE_LOBBY_ATTEMPTS) {
            val lobbyId = LobbyUtils.generateLobbyId()
            val createdLobbyId = withPlayerAndLobbyLock(redissonClient, playerId, lobbyId) {
                val playerLobbyOption = playerLobbyRepository.findById(playerId)
                if (playerLobbyOption.isPresent) {
                    throw PlayerAlreadyInLobbyException(playerId, playerLobbyOption.get().lobbyId)
                }

                if (lobbyRepository.existsById(lobbyId)) {
                    null
                } else {
                    lobbyRepository.save(Lobby(lobbyId).apply {
                        host = playerId
                        players!!.add(playerId)
                    })
                    playerLobbyRepository.save(PlayerLobby(playerId, lobbyId))
                    lobbyId
                }
            }
            if (createdLobbyId != null) {
                return createdLobbyId
            }
        }
        throw LobbyBusyException("Unable to create lobby after $CREATE_LOBBY_ATTEMPTS attempts")
    }

    /**
     * 查询玩家当前所在的大厅。
     *
     * @param playerId 玩家 ID
     * @return 玩家当前所在大厅；未加入大厅时返回 null
     */
    fun getCurrentLobby(playerId: SteamID64): Lobby? {
        val playerLobbyOption = playerLobbyRepository.findById(playerId)
        if (playerLobbyOption.isPresent) {
            return lobbyRepository.findById(playerLobbyOption.get().lobbyId).orElseThrow { PlayerStateIllegalException() }
        }
        return null
    }

    /**
     * 通过 HTTP 将玩家加入目标大厅，并返回最新房间信息。
     *
     * @param playerId 待加入大厅的玩家 ID
     * @param targetLobbyId 目标大厅 ID
     * @return 房间当前完整信息
     * @throws LobbyNotExist 当目标大厅不存在时抛出
     * @throws LobbyBusyException 当目标大厅状态正被其他操作长期占用时抛出
     * @throws PlayerAlreadyInLobbyException 当玩家已属于其他大厅时抛出
     */
    suspend fun joinLobby(playerId: SteamID64, targetLobbyId: LobbyId): Lobby {
        return withPlayerAndLobbyLock(redissonClient, playerId, targetLobbyId) {
            val playerLobbyOption = playerLobbyRepository.findById(playerId)
            // check 玩家当前状态, 已有映射关系时不可加入房间
            // 原本想着后端去处理退出和重新加入，但是这里的竞态关系不好处理，所以先这样
            if (playerLobbyOption.isPresent && playerLobbyOption.get().lobbyId != targetLobbyId) {
                throw PlayerAlreadyInLobbyException(playerId, playerLobbyOption.get().lobbyId)
            }

            val lobby = lobbyRepository.findById(targetLobbyId).orElseThrow { LobbyNotExist() }

            // 只有等待状态的房间才能进
            if (lobby.status != LobbyStatus.WAITING) {
                throw LobbyBusyException("Lobby ${lobby.id} is in status ${lobby.status}, cannot join")
            }

            // 当已经在房间里了，做映射关系强制更新防止数据差错
            if (lobby.players!!.contains(playerId)) {
                playerLobbyRepository.save(PlayerLobby(playerId, targetLobbyId))
                return@withPlayerAndLobbyLock lobby
            }

            // 写入
            lobbyRepository.save(lobby.apply {
                players!!.add(playerId)
            })
            playerLobbyRepository.save(PlayerLobby(playerId, targetLobbyId))

            // 广播
            publishingScope.launch {
                lobby.playerJoin(playerId)
            }
            return@withPlayerAndLobbyLock lobby
        }
    }

    /**
     * 通过 HTTP 将玩家从当前大厅移除，并在大厅为空时删除大厅。
     *
     * @param playerId 待离开大厅的玩家 ID
     * @throws LobbyBusyException 当目标大厅状态正被其他操作长期占用时抛出
     */
    suspend fun leaveLobby(playerId: SteamID64) {
        val playerLobbyOption = withContext(Dispatchers.IO) {
            playerLobbyRepository.findById(playerId)
        }

        if (!playerLobbyOption.isPresent) {
            return
        }

        val targetLobbyId = playerLobbyOption.get().lobbyId

        return withPlayerAndLobbyLock(redissonClient, playerId, targetLobbyId) {
            removePlayerFromLobby(playerId, targetLobbyId)
        }
    }

    /**
     * 把玩家从某个Lobby里移除，提供给持有锁的时候使用，所以这边就不加锁
     *
     * @param playerId 玩家 ID
     * @param targetLobbyId 目标房间id
     */
    private fun removePlayerFromLobby(playerId: SteamID64, targetLobbyId: LobbyId) {
        val playerLobbyOption = playerLobbyRepository.findById(playerId)
        // 清理映射关系
        if (playerLobbyOption.isPresent) { // 因为调用侧已经为playerId和lobbyId加锁了，理应不会产生索引不匹配的情况，所以直接删除即可
            playerLobbyRepository.deleteById(playerId)
        }

        // 查询 lobby
        val lobbyOption = lobbyRepository.findById(targetLobbyId)
        if (!lobbyOption.isPresent) {
            return
        }
        val lobby = lobbyOption.get()

        // 从列表移除
        val removed = lobby.players!!.removeAll { it == playerId }
        if (!removed) {
            playerLobbyRepository.deleteById(playerId)
            return
        }

        // 如果没人了，就销毁
        if (lobby.players!!.isEmpty()) {
            lobbyRepository.deleteById(targetLobbyId)
            playerLobbyRepository.deleteById(playerId)
            publishingScope.launch {
                lobby.playerLeave(playerId)
            }
            return
        }

        // 如果离开的是房主，则需要更新房主
        val nextHost = if (lobby.host == playerId) lobby.players!!.first() else null
        nextHost?.let { lobby.host = it }

        // 写入
        lobbyRepository.save(lobby)

        // 广播
        publishingScope.launch {
            lobby.playerLeave(playerId)
            nextHost?.let {
                lobby.publishHostUpdate(it)
            }
        }
    }

    /**
     * 通过 HTTP 向玩家所在大厅广播文本消息。
     *
     * @param playerId 发送消息的玩家 ID
     * @param content 文本消息内容
     * @throws LobbyNotExist 当目标大厅不存在时抛出
     */
    suspend fun sendTextMessage(playerId: SteamID64, content: String) {
        val playerLobbyOption = withContext(Dispatchers.IO) {
            playerLobbyRepository.findById(playerId)
        }
        if (!playerLobbyOption.isPresent) {
            throw LobbyNotExist()
        }

        val targetLobbyId = playerLobbyOption.get().lobbyId
        val lobbyOption = withContext(Dispatchers.IO) {
            lobbyRepository.findById(targetLobbyId)
        }
        if (!lobbyOption.isPresent) {
            throw LobbyNotExist()
        }

        val lobby = lobbyOption.get()
        if (!lobby.players!!.contains(playerId)) {
            throw LobbyNotExist()
        }
        lobby.playerTexting(playerId, content)
    }

    private fun Lobby.playerTexting(playerId: SteamID64, text: String) {
        redisTemplate.convertAndSend(id.toString(), LobbyMessage (
            LobbyMessageType.TEXTING,
            playerId,
            text,
        ))
    }

    private fun Lobby.publishHostUpdate(newHost: SteamID64) {
        redisTemplate.convertAndSend(id.toString(), LobbyMessage (
            LobbyMessageType.UPDATE_HOST,
            newHost,
            "",
        ))
    }

    private fun Lobby.playerJoin(playerId: SteamID64) {
        redisTemplate.convertAndSend(id.toString(), LobbyMessage(
                LobbyMessageType.JOIN,
                playerId,
                "",
            )
        )
    }

    private fun Lobby.playerLeave(playerId: SteamID64) {
        redisTemplate.convertAndSend(id.toString(), LobbyMessage(
            LobbyMessageType.LEAVE,
            playerId,
            "",
        )
        )
    }
}

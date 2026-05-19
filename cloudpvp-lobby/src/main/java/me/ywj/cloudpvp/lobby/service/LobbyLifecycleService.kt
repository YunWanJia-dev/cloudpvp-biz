package me.ywj.cloudpvp.lobby.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.ywj.cloudpvp.core.model.lobby.LobbyMessage
import me.ywj.cloudpvp.core.model.lobby.LobbyMessageDataTexting
import me.ywj.cloudpvp.core.model.lobby.LobbyMessageType
import me.ywj.cloudpvp.core.model.lobby.LobbyStatus
import me.ywj.cloudpvp.core.type.LobbyId
import me.ywj.cloudpvp.core.type.SteamID64
import me.ywj.cloudpvp.core.utils.LobbyUtils
import me.ywj.cloudpvp.lobby.entity.Lobby
import me.ywj.cloudpvp.lobby.entity.LobbyPlayer
import me.ywj.cloudpvp.lobby.entity.PlayerLobby
import me.ywj.cloudpvp.lobby.exceptions.LobbyBusyException
import me.ywj.cloudpvp.lobby.exceptions.LobbyNotExist
import me.ywj.cloudpvp.lobby.exceptions.PlayerAlreadyInLobbyException
import me.ywj.cloudpvp.lobby.repository.LobbyRepository
import me.ywj.cloudpvp.lobby.repository.PlayerLobbyRepository
import me.ywj.cloudpvp.lobby.utils.RedisLockUtils.withLobbyLock
import me.ywj.cloudpvp.lobby.utils.RedisLockUtils.withPlayerAndLobbyLock
import org.redisson.api.RedissonClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.listener.PatternTopic
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import org.springframework.stereotype.Service

/**
 * LobbyLifecycleService
 * 大厅生命周期管理：创建、加入、退出、订阅、文本消息等基础操作。
 *
 * @author sheip9
 * @since 2024/10/20 16:35
 */
@Service
class LobbyLifecycleService @Autowired constructor(
    val lobbyRepository: LobbyRepository,
    val playerLobbyRepository: PlayerLobbyRepository,
    val redisTemplate: RedisTemplate<String, Any>,
    val redissonClient: RedissonClient,
    val container: RedisMessageListenerContainer,
) {
    companion object {
        private const val CREATE_LOBBY_ATTEMPTS = 8
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
    suspend fun getCurrentLobby(playerId: SteamID64): Lobby? {
        val playerLobbyOption = withContext(Dispatchers.IO) {
            playerLobbyRepository.findById(playerId)
        }
        if (!playerLobbyOption.isPresent) {
            return null
        }

        return withContext(Dispatchers.IO) {
            lobbyRepository.findById(playerLobbyOption.get().lobbyId).orElse(null)
        }
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
            if (playerLobbyOption.isPresent && playerLobbyOption.get().lobbyId != targetLobbyId) {
                throw PlayerAlreadyInLobbyException(playerId, playerLobbyOption.get().lobbyId)
            }

            val lobbyOption = lobbyRepository.findById(targetLobbyId)
            if (!lobbyOption.isPresent) {
                throw LobbyNotExist()
            }

            val lobby = lobbyOption.get()
            if (lobby.status != LobbyStatus.WAITING) {
                throw LobbyBusyException("Lobby ${lobby.id} is in status ${lobby.status}, cannot join")
            }
            val players = lobby.players!!
            val alreadyInLobby = players.contains(playerId)
            if (alreadyInLobby) {
                playerLobbyRepository.save(PlayerLobby(playerId, targetLobbyId))
                return@withPlayerAndLobbyLock lobby
            }
            players.add(playerId)
            lobbyRepository.save(lobby)
            playerLobbyRepository.save(PlayerLobby(playerId, targetLobbyId))
            lobby.sendMsg(LobbyMessage(LobbyMessageType.JOIN).apply {
                data = playerId
            })
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
        withPlayerAndLobbyLock(redissonClient, playerId, targetLobbyId) {
            val playerLobbyOption = playerLobbyRepository.findById(playerId)
            if (playerLobbyOption.isEmpty || targetLobbyId != playerLobbyOption.get().lobbyId) {
                return@withPlayerAndLobbyLock
            }
            val lobbyOption = lobbyRepository.findById(targetLobbyId)
            if (!lobbyOption.isPresent) {
                playerLobbyRepository.deleteById(playerId)
                return@withPlayerAndLobbyLock
            }

            val lobby = lobbyOption.get()
            val removed = lobby.players!!.removeAll { it == playerId }

            if (!removed) {
                playerLobbyRepository.deleteById(playerId)
                return@withPlayerAndLobbyLock
            }

            if (lobby.players!!.isEmpty()) {
                lobby.sendMsg(LobbyMessage(LobbyMessageType.LOBBY_DESTROYED))
                lobbyRepository.deleteById(targetLobbyId)
                playerLobbyRepository.deleteById(playerId)
                return@withPlayerAndLobbyLock
            }

            val nextHost = if (lobby.host == playerId) lobby.players!!.first() else null
            nextHost?.let { lobby.host = it }

            lobbyRepository.save(lobby)
            playerLobbyRepository.deleteById(playerId)
            nextHost?.let { lobby.publishHostUpdate(it) }
            lobby.sendMsg(LobbyMessage(LobbyMessageType.LEAVE).apply {
                data = playerId
            })
        }
    }

    /**
     * 为当前 WebSocket 连接注册目标大厅的消息监听器，并发送一次大厅快照。
     *
     * @param player 当前 socket 绑定的玩家连接状态
     * @param targetLobbyId 目标大厅 ID
     * @return true 表示玩家已在大厅内、完成监听注册并已发送当前大厅快照
     * @throws LobbyNotExist 当目标大厅不存在时抛出
     * @throws LobbyBusyException 当目标大厅状态正被其他操作长期占用时抛出
     */
    suspend fun subscribeLobby(player: LobbyPlayer, targetLobbyId: LobbyId): Boolean {
        val subscribed = withLobbyLock(redissonClient, targetLobbyId) {
            val lobbyOption = lobbyRepository.findById(targetLobbyId)
            if (!lobbyOption.isPresent) {
                throw LobbyNotExist()
            }

            val lobby = lobbyOption.get()
            if (!lobby.players!!.contains(player.steamID64)) {
                return@withLobbyLock false
            }
            val topic = PatternTopic(lobby.id.toString())
            player.lobbyId = targetLobbyId
            container.addMessageListener(player.msgListener, topic)
            player.sendMessage(LobbyMessage(LobbyMessageType.LOBBY_SNAPSHOT).apply {
                data = lobby
            })
            true
        }

        return subscribed
    }

    /**
     * 移除当前 WebSocket 连接注册的大厅消息监听器。
     *
     * @param player 当前 socket 绑定的玩家连接状态
     */
    fun unsubscribeLobby(player: LobbyPlayer) {
        val targetLobbyId = player.lobbyId ?: return
        container.removeMessageListener(player.msgListener, PatternTopic(targetLobbyId.toString()))
        player.lobbyId = null
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
        lobby.sendMsg(LobbyMessage(LobbyMessageType.TEXTING).apply {
            data = LobbyMessageDataTexting(playerId, content)
        })
    }

    private suspend fun Lobby.sendMsg(msg: Any) {
        withContext(Dispatchers.IO) {
            redisTemplate.convertAndSend(id.toString(), msg)
        }
    }

    private suspend fun Lobby.publishHostUpdate(newHost: SteamID64) {
        sendMsg(LobbyMessage(LobbyMessageType.UPDATE_HOST).apply {
            data = newHost
        })
    }
}

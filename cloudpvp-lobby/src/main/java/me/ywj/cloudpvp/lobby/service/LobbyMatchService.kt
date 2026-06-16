package me.ywj.cloudpvp.lobby.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.ywj.cloudpvp.beans.property.PlayProperty
import me.ywj.cloudpvp.core.model.lobby.LobbyMessage
import me.ywj.cloudpvp.core.model.lobby.LobbyMessageType
import me.ywj.cloudpvp.core.model.lobby.LobbyStatus
import me.ywj.cloudpvp.core.type.LobbyId
import me.ywj.cloudpvp.core.type.SteamID64
import me.ywj.cloudpvp.lobby.configurations.RabbitMQConfiguration
import me.ywj.cloudpvp.lobby.constant.routingkey.MatchmakingKey
import me.ywj.cloudpvp.lobby.entity.Lobby
import me.ywj.cloudpvp.lobby.exceptions.LobbyBusyException
import me.ywj.cloudpvp.lobby.exceptions.LobbyNotExist
import me.ywj.cloudpvp.lobby.model.SelectModeDTO
import me.ywj.cloudpvp.lobby.repository.LobbyRepository
import me.ywj.cloudpvp.lobby.utils.RedisLockUtils.withLobbyLock
import org.redisson.api.RedissonClient
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service

/**
 * LobbyMatchService
 * 大厅匹配流程管理：选择模式、开始/停止匹配、确认比赛。
 *
 * @author sheip9
 * @since 2026/5/19 14:30
 */
@Service
class LobbyMatchService @Autowired constructor(
    val lobbyRepository: LobbyRepository,
    val redisTemplate: RedisTemplate<String, Any>,
    val redissonClient: RedissonClient,
    val playProperty: PlayProperty,
    val rabbitTemplate: RabbitTemplate,
) {
    /**
     * 选择游戏模式。只有房主可在 WAITING 状态下修改。
     *
     * @param lobbyId 目标大厅 ID
     * @param playerId 发起请求的玩家 ID
     * @param request 选择模式请求体
     * @throws LobbyNotExist 当目标大厅不存在时抛出
     * @throws LobbyBusyException 当玩家不是房主、大厅状态不正确或模式 key 无效时抛出
     */
    suspend fun selectMode(lobbyId: LobbyId, playerId: SteamID64, request: SelectModeDTO) {
        withLobbyLock(redissonClient, lobbyId) {
            val lobby = getLobbyOrThrow(lobbyId)
            if (lobby.host != playerId) {
                throw LobbyBusyException("Player $playerId is not the host of lobby $lobbyId")
            }
            if (lobby.status != LobbyStatus.WAITING) {
                throw LobbyBusyException("Lobby $lobbyId is in status ${lobby.status}, cannot select mode")
            }

            val modeExists = playProperty.games.any { game ->
                game.key == request.gameKey && game.types.any { type ->
                    type.key == request.typeKey && type.modes.any { mode ->
                        mode.key == request.modeKey
                    }
                }
            }
            if (!modeExists) {
                throw LobbyBusyException("Invalid mode: ${request.gameKey}/${request.typeKey}/${request.modeKey}")
            }

            lobby.gameKey = request.gameKey
            lobby.typeKey = request.typeKey
            lobby.modeKey = request.modeKey
            lobbyRepository.save(lobby)
            lobby.sendMsg(LobbyMessage(LobbyMessageType.LOBBY_SNAPSHOT).apply {
                data = lobby
            })
        }
    }

    /**
     * 开始匹配。只有房主可触发，将大厅状态设为 MATCHING 并通知所有玩家。
     *
     * @param lobbyId 目标大厅 ID
     * @param playerId 发起请求的玩家 ID
     * @throws LobbyNotExist 当目标大厅不存在时抛出
     * @throws LobbyBusyException 当玩家不是房主或大厅状态不正确时抛出
     */
    suspend fun startMatching(lobbyId: LobbyId, playerId: SteamID64) {
        withLobbyLock(redissonClient, lobbyId) {
            val lobby = getLobbyOrThrow(lobbyId)
            if (lobby.host != playerId) {
                throw LobbyBusyException("Player $playerId is not the host of lobby $lobbyId")
            }
            if (lobby.status != LobbyStatus.WAITING) {
                throw LobbyBusyException("Lobby $lobbyId is in status ${lobby.status}, cannot start matching")
            }
            if (lobby.gameKey == null || lobby.typeKey == null || lobby.modeKey == null) {
                throw LobbyBusyException("Lobby $lobbyId has no game mode selected, cannot start matching")
            }

            lobby.status = LobbyStatus.MATCHING
            lobbyRepository.save(lobby)
            lobby.sendMsg(LobbyMessage(LobbyMessageType.MATCH_START))
            withContext(Dispatchers.IO) {
                rabbitTemplate.convertAndSend(
                    RabbitMQConfiguration.MATCHMAKING_EXCHANGE_NAME,
                    MatchmakingKey.Submit.routingKey,
                    lobby,
                )
            }
        }
    }

    /**
     * 停止匹配。只有房主可触发，将大厅状态恢复为 WAITING 并通知所有玩家。
     *
     * @param lobbyId 目标大厅 ID
     * @param playerId 发起请求的玩家 ID
     * @throws LobbyNotExist 当目标大厅不存在时抛出
     * @throws LobbyBusyException 当玩家不是房主或大厅状态不正确时抛出
     */
    suspend fun stopMatching(lobbyId: LobbyId, playerId: SteamID64) {
        withLobbyLock(redissonClient, lobbyId) {
            val lobby = getLobbyOrThrow(lobbyId)
            if (lobby.host != playerId) {
                throw LobbyBusyException("Player $playerId is not the host of lobby $lobbyId")
            }
            if (lobby.status != LobbyStatus.MATCHING) {
                throw LobbyBusyException("Lobby $lobbyId is in status ${lobby.status}, cannot stop matching")
            }

            lobby.status = LobbyStatus.WAITING
            lobbyRepository.save(lobby)
            lobby.sendMsg(LobbyMessage(LobbyMessageType.MATCH_STOP))
            withContext(Dispatchers.IO) {
                rabbitTemplate.convertAndSend(
                    RabbitMQConfiguration.MATCHMAKING_EXCHANGE_NAME,
                    MatchmakingKey.Cancel.routingKey,
                    lobbyId,
                )
            }
        }
    }

    /**
     * 确认比赛。将确认信息通过 MQ 发送给匹配模块，由匹配模块统计所有玩家确认后通知本服务更新状态。
     *
     * @param lobbyId 目标大厅 ID
     * @param playerId 发起确认的玩家 ID
     * @throws LobbyNotExist 当目标大厅不存在时抛出
     * @throws LobbyBusyException 当大厅状态不正确或玩家不在大厅中时抛出
     */
    suspend fun confirmMatch(lobbyId: LobbyId, playerId: SteamID64) {
        withLobbyLock(redissonClient, lobbyId) {
            val lobby = getLobbyOrThrow(lobbyId)
            if (lobby.status != LobbyStatus.MATCHED) {
                throw LobbyBusyException("Lobby $lobbyId is in status ${lobby.status}, cannot confirm match")
            }
            if (!lobby.players!!.contains(playerId)) {
                throw LobbyBusyException("Player $playerId is not in lobby $lobbyId")
            }

            lobby.sendMsg(LobbyMessage(LobbyMessageType.MATCH_CONFIRM).apply {
                data = playerId
            })
            // TODO: 通过 MQ 发送玩家确认消息给匹配模块，由匹配模块统计并下发确认结果
        }
    }

    private suspend fun getLobbyOrThrow(lobbyId: LobbyId): Lobby {
        val lobbyOption = withContext(Dispatchers.IO) {
            lobbyRepository.findById(lobbyId)
        }
        if (!lobbyOption.isPresent) {
            throw LobbyNotExist()
        }
        return lobbyOption.get()
    }

    private suspend fun Lobby.sendMsg(msg: Any) {
        withContext(Dispatchers.IO) {
            redisTemplate.convertAndSend(id.toString(), msg)
        }
    }
}

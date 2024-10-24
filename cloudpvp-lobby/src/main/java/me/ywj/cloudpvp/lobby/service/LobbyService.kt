package me.ywj.cloudpvp.lobby.service

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.ywj.cloudpvp.core.constant.lobby.LobbyConstant
import me.ywj.cloudpvp.core.type.LobbyId
import me.ywj.cloudpvp.core.type.toLobbyId
import me.ywj.cloudpvp.lobby.entity.Lobby
import me.ywj.cloudpvp.lobby.entity.LobbyPlayer
import me.ywj.cloudpvp.lobby.exception.LobbyNotExist
import me.ywj.cloudpvp.lobby.repository.LobbyRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.listener.PatternTopic
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import org.springframework.stereotype.Service
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

/**
 * LobbyService
 *
 * @author sheip9
 * @since 2024/10/20 16:35
 */
@Service
class LobbyService @Autowired constructor(val lobbyRepository : LobbyRepository, val redisTemplate: RedisTemplate<Number, Lobby>, val container: RedisMessageListenerContainer) {
    
    @OptIn(DelicateCoroutinesApi::class, ExperimentalTime::class)
    fun createLobby() : LobbyId {
        val sb = StringBuilder()
        for (i in 1..LobbyConstant.ID_SIZE) {
            sb.append(Random.nextInt(10))
        }
        val lobbyId = sb.toString().toLobbyId()
        
        if (lobbyRepository.findById(lobbyId).isPresent) {
            //如果生成的id已存在，则重新生成
            return createLobby()
        }
        
        val lobby = Lobby(lobbyId)
        
        //特定时间过后 “创建房间”的玩家未能加入 则清理掉
        GlobalScope.launch {
            delay((LobbyConstant.CREATE_TIMEOUT).seconds)
            val lobbyOption = lobbyRepository.findById(lobbyId)
            if (!lobbyOption.isPresent) {
                return@launch
            }
            val lobby = lobbyOption.get()
            if(lobby.players!!.isEmpty()) {
                lobbyRepository.deleteById(lobbyId)
            }
        }
        
        lobbyRepository.save(lobby)
        return lobbyId
    }
    
    fun joinLobby(player: LobbyPlayer) {
        val targetLobbyId = player.lobbyId
        val lobbyOption = lobbyRepository.findById(targetLobbyId)
        if (!lobbyOption.isPresent) {
            throw LobbyNotExist()
        }
        val lobby = lobbyOption.get().apply {
            players!!.add(player.steamId64)
        }
        container.addMessageListener(player.msgListener, PatternTopic(lobby.id.toString()))
        lobbyRepository.save(lobby)
    }
    
    fun leaveLobby(player: LobbyPlayer) {
        val targetLobbyId = player.lobbyId
        val lobbyOption = lobbyRepository.findById(targetLobbyId)
        if(!lobbyOption.isPresent) {
            return
        }
        val lobby = lobbyOption.get()
        lobby.players!!.remove(player.steamId64)
        if(lobby.players!!.isEmpty()) {
            return lobbyRepository.deleteById(targetLobbyId)
        }
        container.removeMessageListener(player.msgListener, PatternTopic(lobby.id.toString()))
        lobbyRepository.save(lobby)
    }
    
    fun Lobby.sendMsg(msg: Any) {
        redisTemplate.convertAndSend(this.id.toString(), msg)    
    }
    
}
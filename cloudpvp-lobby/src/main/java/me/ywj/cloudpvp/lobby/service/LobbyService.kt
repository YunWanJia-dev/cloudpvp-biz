package me.ywj.cloudpvp.lobby.service

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.ywj.cloudpvp.core.constant.lobby.LobbyConstant
import me.ywj.cloudpvp.core.entity.BasicPlayer
import me.ywj.cloudpvp.lobby.entity.Lobby
import me.ywj.cloudpvp.lobby.exception.LobbyNotExist
import me.ywj.cloudpvp.lobby.repository.LobbyRepository
import org.springframework.beans.factory.annotation.Autowired
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
class LobbyService @Autowired constructor(val lobbyRepository : LobbyRepository) {
    @OptIn(DelicateCoroutinesApi::class, ExperimentalTime::class)
    fun createLobby() : String {
        val sb = StringBuilder()
        for (i in 1..8) {
            sb.append(Random.nextInt(10))
        }
        val lobbyIdStr = sb.toString()
        val lobbyIdNum = lobbyIdStr.toInt()
        val lobby = Lobby(lobbyIdNum)
        
        //特定时间过后 “创建房间”的玩家未能加入 则清理掉
        GlobalScope.launch {
            delay((LobbyConstant.CREATE_TIMEOUT).seconds)
            val lobbyOption = lobbyRepository.findById(lobbyIdNum)
            if (!lobbyOption.isPresent) {
                return@launch
            }
            val lobby = lobbyOption.get()
            if(lobby.players!!.isEmpty()) {
                lobbyRepository.deleteById(lobbyIdNum)
            }
        }
        
        lobbyRepository.save(lobby)
        return lobbyIdStr
    }
    
    fun joinLobby(player: BasicPlayer, targetLobbyId : Int) {
        val lobbyOption = lobbyRepository.findById(targetLobbyId)
        if (!lobbyOption.isPresent) {
            throw LobbyNotExist()
        }
        val lobby = lobbyOption.get().apply {
            players!!.add(player.steamId64)
        }
        lobbyRepository.save(lobby)
    }
    
    fun leaveLobby(player: BasicPlayer, targetLobbyId : Int) {
        val lobbyOption = lobbyRepository.findById(targetLobbyId)
        if(!lobbyOption.isPresent) {
            return
        }
        val lobby = lobbyOption.get()
        lobby.players!!.remove(player.steamId64)
        if(lobby.players!!.isEmpty()) {
            return lobbyRepository.deleteById(targetLobbyId)
        }
        lobbyRepository.save(lobby)
    }
}
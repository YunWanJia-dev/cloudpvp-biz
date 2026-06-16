package me.ywj.cloudpvp.lobby.service

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.ywj.cloudpvp.core.model.lobby.LobbyMessage
import me.ywj.cloudpvp.core.model.lobby.LobbyMessageType
import me.ywj.cloudpvp.lobby.entity.Lobby
import me.ywj.cloudpvp.lobby.entity.LobbyPlayer
import me.ywj.cloudpvp.lobby.entity.PlayerLobby
import me.ywj.cloudpvp.lobby.configurations.RedisConfiguration
import me.ywj.cloudpvp.lobby.exceptions.LobbyBusyException
import me.ywj.cloudpvp.lobby.exceptions.LobbyNotExist
import me.ywj.cloudpvp.lobby.exceptions.PlayerAlreadyInLobbyException
import me.ywj.cloudpvp.lobby.repository.LobbyRepository
import me.ywj.cloudpvp.lobby.repository.PlayerLobbyRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.argThat
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.timeout
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.redisson.api.RFuture
import org.redisson.api.RLock
import org.redisson.api.RedissonClient
import org.springframework.data.redis.connection.Message
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.listener.PatternTopic
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import java.util.Optional
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.function.BiConsumer
import kotlin.test.assertFailsWith

/**
 * LobbyServiceTest
 * 大厅服务单元测试。
 *
 * @author sheip9
 * @since 2026/5/16 16:57
 */
class LobbyServiceTest {
    private companion object {
        const val LOCK_WAIT_MILLIS = 200L
        const val LOCK_WATCHDOG_LEASE_MILLIS = -1L
        const val LOCK_ATTEMPTS = 5
    }

    /**
     * 验证创建大厅通过 Repository 保存，并将创建者写入初始房主和玩家列表。
     */
    @Test
    fun createLobbySavesNewLobbyThroughRepository() = runTest {
        val fixture = createFixture(true)
        val playerId = 456L
        `when`(fixture.lobbyRepository.existsById(any(Number::class.java))).thenReturn(false)
        `when`(fixture.lobbyRepository.save(any(Lobby::class.java))).thenAnswer { it.getArgument(0) }

        val lobbyId = fixture.lobbyService.createLobby(playerId)

        assertThat(lobbyId).isPositive()
        verify(fixture.redissonClient).getLock("LobbyPlayerLock:$playerId")
        verify(fixture.redissonClient).getLock("LobbyLock:$lobbyId")
        verify(fixture.lock).tryLockAsync(
            eq(LOCK_WAIT_MILLIS),
            eq(LOCK_WATCHDOG_LEASE_MILLIS),
            eq(TimeUnit.MILLISECONDS),
            anyLong(),
        )
        verify(fixture.playerLobbyRepository).findById(playerId)
        verify(fixture.lobbyRepository).existsById(lobbyId)
        verify(fixture.lobbyRepository).save(argThat { lobby ->
            // 新接口在创建时已经绑定创建者，测试要覆盖这个调用方可见的状态变化。
            lobby.id == lobbyId &&
                lobby.host == playerId &&
                lobby.players == arrayListOf(playerId)
        })
        verify(fixture.playerLobbyRepository).save(argThat { playerLobby ->
            playerLobby.playerId == playerId && playerLobby.lobbyId == lobbyId
        })
        Mockito.inOrder(fixture.lobbyRepository, fixture.playerLobbyRepository).apply {
            verify(fixture.lobbyRepository).save(any(Lobby::class.java))
            verify(fixture.playerLobbyRepository).save(any(PlayerLobby::class.java))
        }
        verify(fixture.lock).unlockAsync(anyLong())
        verifyNoInteractions(fixture.redisTemplate)
    }

    /**
     * 验证 Repository 已存在实体时释放锁并重新生成，避免覆盖同 ID 大厅。
     */
    @Test
    fun createLobbyRetriesWhenRepositoryAlreadyHasLobby() = runTest {
        val fixture = createFixture(true, true)
        val playerId = 456L
        `when`(fixture.lobbyRepository.existsById(any(Number::class.java))).thenReturn(true, false)
        `when`(fixture.lobbyRepository.save(any(Lobby::class.java))).thenAnswer { it.getArgument(0) }

        val lobbyId = fixture.lobbyService.createLobby(playerId)

        assertThat(lobbyId).isPositive()
        verify(fixture.redissonClient, times(4)).getLock(anyString())
        verify(fixture.lobbyRepository, times(2)).existsById(any(Number::class.java))
        verify(fixture.lobbyRepository).save(argThat { lobby ->
            lobby.id == lobbyId &&
                lobby.host == playerId &&
                lobby.players == arrayListOf(playerId)
        })
        verify(fixture.playerLobbyRepository).save(argThat { playerLobby ->
            playerLobby.playerId == playerId && playerLobby.lobbyId == lobbyId
        })
        verify(fixture.lock, times(2)).unlockAsync(anyLong())
        verifyNoInteractions(fixture.redisTemplate)
    }

    /**
     * 验证获取不到 Redis 锁时不会写入 Repository。
     */
    @Test
    fun createLobbyFailsWhenLockCannotBeAcquired() = runTest {
        val fixture = createFixture(false, false, false, false, false)

        assertFailsWith<LobbyBusyException> {
            fixture.lobbyService.createLobby(456L)
        }

        verify(fixture.lock, times(LOCK_ATTEMPTS)).tryLockAsync(
            eq(LOCK_WAIT_MILLIS),
            eq(LOCK_WATCHDOG_LEASE_MILLIS),
            eq(TimeUnit.MILLISECONDS),
            anyLong(),
        )
        verify(fixture.lock, never()).unlockAsync(anyLong())
        verifyNoInteractions(fixture.lobbyRepository, fixture.playerLobbyRepository, fixture.redisTemplate)
    }

    /**
     * 验证已有当前大厅索引时不会再创建第二个大厅。
     */
    @Test
    fun createLobbyRejectsWhenPlayerAlreadyHasMembership() = runTest {
        val fixture = createFixture(true)
        val playerId = 456L
        `when`(fixture.playerLobbyRepository.findById(playerId))
            .thenReturn(Optional.of(PlayerLobby(playerId, 123)))

        assertFailsWith<PlayerAlreadyInLobbyException> {
            fixture.lobbyService.createLobby(playerId)
        }

        verify(fixture.redissonClient).getLock("LobbyPlayerLock:$playerId")
        verify(fixture.playerLobbyRepository).findById(playerId)
        verify(fixture.lock).unlockAsync(anyLong())
        verifyNoInteractions(fixture.lobbyRepository, fixture.redisTemplate, fixture.container)
    }

    /**
     * 验证查询当前大厅时直接返回玩家索引指向的有效大厅。
     */
    @Test
    fun getCurrentLobbyReturnsMembershipLobby() = runTest {
        val fixture = createFixture(true)
        val playerId = 456L
        val lobby = Lobby(123, arrayListOf(111L, playerId))
        lobby.host = 111L
        `when`(fixture.playerLobbyRepository.findById(playerId))
            .thenReturn(Optional.of(PlayerLobby(playerId, 123)))
        `when`(fixture.lobbyRepository.findById(123)).thenReturn(Optional.of(lobby))

        val currentLobby = fixture.lobbyService.getCurrentLobby(playerId)

        assertThat(currentLobby).isSameAs(lobby)
        verify(fixture.playerLobbyRepository).findById(playerId)
        verify(fixture.lobbyRepository).findById(123)
        verifyNoInteractions(fixture.redisTemplate, fixture.redissonClient, fixture.lock, fixture.container)
    }

    /**
     * 验证玩家没有大厅索引时查询当前大厅返回 null。
     */
    @Test
    fun getCurrentLobbyReturnsNullWithoutMembership() = runTest {
        val fixture = createFixture(true)

        val currentLobby = fixture.lobbyService.getCurrentLobby(456L)

        assertThat(currentLobby).isNull()
        verify(fixture.playerLobbyRepository).findById(456L)
        verify(fixture.lobbyRepository, never()).findById(any(Number::class.java))
        verifyNoInteractions(fixture.redisTemplate, fixture.redissonClient, fixture.lock, fixture.container)
    }

    /**
     * 验证协程取消和 Redisson 授权并发发生时，已经归属当前 owner 的锁仍会被释放。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun createLobbyUnlocksWhenCancelledAfterAsyncLockIsGranted() = runTest {
        val lobbyRepository = Mockito.mock(LobbyRepository::class.java)
        val playerLobbyRepository = Mockito.mock(PlayerLobbyRepository::class.java)
        @Suppress("UNCHECKED_CAST")
        val redisTemplate = Mockito.mock(RedisTemplate::class.java) as RedisTemplate<String, Any>
        val redissonClient = Mockito.mock(RedissonClient::class.java)
        val lock = Mockito.mock(RLock::class.java)
        val container = Mockito.mock(RedisMessageListenerContainer::class.java)
        @Suppress("UNCHECKED_CAST")
        val lockFuture = Mockito.mock(RFuture::class.java) as RFuture<Boolean>
        @Suppress("UNCHECKED_CAST")
        val unlockFuture = completedFuture(null) as RFuture<Void>
        val completion = AtomicReference<BiConsumer<Boolean, Throwable?>>()

        `when`(redissonClient.getLock(anyString())).thenReturn(lock)
        `when`(redissonClient.getMultiLock(any(RLock::class.java), any(RLock::class.java))).thenReturn(lock)
        `when`(
            lock.tryLockAsync(
                eq(LOCK_WAIT_MILLIS),
                eq(LOCK_WATCHDOG_LEASE_MILLIS),
                eq(TimeUnit.MILLISECONDS),
                anyLong(),
            ),
        ).thenReturn(lockFuture)
        `when`(lock.unlockAsync(anyLong())).thenReturn(unlockFuture)
        `when`(lockFuture.whenComplete(any())).thenAnswer { invocation ->
            completion.set(invocation.getArgument(0))
            lockFuture
        }

        val lobbyService = LobbyLifecycleService(lobbyRepository, playerLobbyRepository, redisTemplate, redissonClient, container)
        val createJob = launch {
            lobbyService.createLobby(456L)
        }
        runCurrent()

        createJob.cancel()
        completion.get().accept(true, null)
        createJob.join()

        verify(lock).unlockAsync(anyLong())
        verifyNoInteractions(lobbyRepository, playerLobbyRepository, redisTemplate, container)
    }

    /**
     * 验证加入大厅时在同一把 lobby 锁内读取、修改并保存玩家列表。
     */
    @Test
    fun joinLobbySavesPlayersUnderLobbyLock() = runTest {
        val fixture = createFixture(true)
        val playerId = 456L
        val lobby = Lobby(123, arrayListOf(111L))
        lobby.host = 111L
        `when`(fixture.lobbyRepository.findById(123)).thenReturn(Optional.of(lobby))
        `when`(fixture.lobbyRepository.save(any(Lobby::class.java))).thenAnswer { it.getArgument(0) }

        val updatedLobby = fixture.lobbyService.joinLobby(playerId, 123)

        assertThat(updatedLobby).isSameAs(lobby)
        verify(fixture.redissonClient).getLock("LobbyPlayerLock:$playerId")
        verify(fixture.redissonClient).getLock("LobbyLock:123")
        verify(fixture.lobbyRepository).save(argThat { savedLobby ->
            savedLobby.id == 123 &&
                savedLobby.host == 111L &&
                savedLobby.players!!.containsAll(listOf(111L, playerId))
        })
        verify(fixture.playerLobbyRepository).save(argThat { playerLobby ->
            playerLobby.playerId == playerId && playerLobby.lobbyId == 123
        })
        Mockito.inOrder(fixture.lobbyRepository, fixture.playerLobbyRepository).apply {
            verify(fixture.lobbyRepository).save(any(Lobby::class.java))
            verify(fixture.playerLobbyRepository).save(any(PlayerLobby::class.java))
        }
        verify(fixture.redisTemplate, timeout(5_000)).convertAndSend(eq("123"), argThat { message ->
            // HTTP 加入只负责写入状态并广播 JOIN，WebSocket 监听注册由 subscribeLobby 单独覆盖。
            message is LobbyMessage &&
                message.type == LobbyMessageType.JOIN &&
                message.data == playerId
        })
        verify(fixture.lock).unlockAsync(anyLong())
        verifyNoInteractions(fixture.container)
    }

    /**
     * 验证玩家已有其他大厅索引时拒绝加入目标大厅，避免存储中出现多大厅成员关系。
     */
    @Test
    fun joinLobbyRejectsWhenPlayerAlreadyBelongsToAnotherLobby() = runTest {
        val fixture = createFixture(true)
        val playerId = 456L
        `when`(fixture.playerLobbyRepository.findById(playerId))
            .thenReturn(Optional.of(PlayerLobby(playerId, 321)))

        assertFailsWith<PlayerAlreadyInLobbyException> {
            fixture.lobbyService.joinLobby(playerId, 123)
        }

        verify(fixture.redissonClient).getLock("LobbyPlayerLock:$playerId")
        verify(fixture.lock).unlockAsync(anyLong())
        verifyNoInteractions(fixture.lobbyRepository, fixture.redisTemplate, fixture.container)
    }

    /**
     * 验证重复加入当前大厅时刷新玩家索引，不重复保存大厅或广播加入消息。
     */
    @Test
    fun joinLobbyRefreshesMembershipIndexWhenMembershipAlreadyTargetsLobby() = runTest {
        val fixture = createFixture(true)
        val playerId = 456L
        val lobby = Lobby(123, arrayListOf(111L, playerId))
        lobby.host = 111L
        `when`(fixture.playerLobbyRepository.findById(playerId))
            .thenReturn(Optional.of(PlayerLobby(playerId, 123)))
        `when`(fixture.lobbyRepository.findById(123)).thenReturn(Optional.of(lobby))

        val updatedLobby = fixture.lobbyService.joinLobby(playerId, 123)

        assertThat(updatedLobby).isSameAs(lobby)
        verify(fixture.lobbyRepository, never()).save(any(Lobby::class.java))
        verify(fixture.playerLobbyRepository).save(argThat { playerLobby ->
            playerLobby.playerId == playerId && playerLobby.lobbyId == 123
        })
        verifyNoInteractions(fixture.redisTemplate, fixture.container)
        verify(fixture.lock).unlockAsync(anyLong())
    }

    /**
     * 验证成员列表已有玩家但索引缺失时，重复加入只补写玩家索引。
     */
    @Test
    fun joinLobbyRepairsMissingMembershipIndexWhenPlayerAlreadyInLobby() = runTest {
        val fixture = createFixture(true)
        val playerId = 456L
        val lobby = Lobby(123, arrayListOf(111L, playerId))
        lobby.host = 111L
        `when`(fixture.lobbyRepository.findById(123)).thenReturn(Optional.of(lobby))

        val updatedLobby = fixture.lobbyService.joinLobby(playerId, 123)

        assertThat(updatedLobby).isSameAs(lobby)
        verify(fixture.lobbyRepository, never()).save(any(Lobby::class.java))
        verify(fixture.playerLobbyRepository).save(argThat { playerLobby ->
            playerLobby.playerId == playerId && playerLobby.lobbyId == 123
        })
        verifyNoInteractions(fixture.redisTemplate, fixture.container)
        verify(fixture.lock).unlockAsync(anyLong())
    }

    /**
     * 验证索引指向其他大厅时优先拒绝加入，不读取目标大厅成员列表覆写索引。
     */
    @Test
    fun joinLobbyRejectsMismatchedMembershipIndexBeforeReadingTargetLobby() = runTest {
        val fixture = createFixture(true)
        val playerId = 456L
        `when`(fixture.playerLobbyRepository.findById(playerId))
            .thenReturn(Optional.of(PlayerLobby(playerId, 321)))

        assertFailsWith<PlayerAlreadyInLobbyException> {
            fixture.lobbyService.joinLobby(playerId, 123)
        }

        verifyNoInteractions(fixture.lobbyRepository, fixture.redisTemplate, fixture.container)
        verify(fixture.lock).unlockAsync(anyLong())
    }

    /**
     * 验证离开大厅时复用同一把 lobby 锁，避免和加入、清理流程并发覆盖。
     */
    @Test
    fun leaveLobbyDeletesEmptyLobbyUnderLobbyLock() = runTest {
        val fixture = createFixture(true)
        val playerId = 456L
        val lobby = Lobby(123, arrayListOf(456L))
        lobby.host = 456L
        stubMembershipUntilDeleted(fixture.playerLobbyRepository, PlayerLobby(playerId, 123))
        `when`(fixture.lobbyRepository.findById(123)).thenReturn(Optional.of(lobby))

        fixture.lobbyService.leaveLobby(playerId)

        verify(fixture.redissonClient).getLock("LobbyPlayerLock:$playerId")
        verify(fixture.redissonClient).getLock("LobbyLock:123")
        verify(fixture.lobbyRepository).deleteById(123)
        verify(fixture.playerLobbyRepository).deleteById(playerId)
        verify(fixture.redisTemplate, timeout(5_000)).convertAndSend(eq("123"), argThat { message ->
            message is LobbyMessage &&
                message.type == LobbyMessageType.LOBBY_DESTROYED
        })
        verify(fixture.lock).unlockAsync(anyLong())
        verifyNoInteractions(fixture.container)
    }

    /**
     * 验证退出大厅时索引指向的大厅不存在会静默删除当前玩家索引。
     */
    @Test
    fun leaveLobbyDeletesMembershipWhenIndexedLobbyDoesNotExist() = runTest {
        val fixture = createFixture(true)
        val playerId = 456L
        stubMembershipUntilDeleted(fixture.playerLobbyRepository, PlayerLobby(playerId, 123))
        `when`(fixture.lobbyRepository.findById(123)).thenReturn(Optional.empty())

        fixture.lobbyService.leaveLobby(playerId)

        verify(fixture.lobbyRepository).findById(123)
        verify(fixture.playerLobbyRepository).deleteById(playerId)
        verify(fixture.lobbyRepository, never()).save(any(Lobby::class.java))
        verify(fixture.lock).unlockAsync(anyLong())
        verifyNoInteractions(fixture.container, fixture.redisTemplate)
    }

    /**
     * 验证退出大厅时玩家已不在成员列表会静默删除当前玩家索引。
     */
    @Test
    fun leaveLobbyDeletesMembershipWhenPlayerIsNotInIndexedLobby() = runTest {
        val fixture = createFixture(true)
        val playerId = 456L
        val lobby = Lobby(123, arrayListOf(111L))
        lobby.host = 111L
        stubMembershipUntilDeleted(fixture.playerLobbyRepository, PlayerLobby(playerId, 123))
        `when`(fixture.lobbyRepository.findById(123)).thenReturn(Optional.of(lobby))

        fixture.lobbyService.leaveLobby(playerId)

        verify(fixture.playerLobbyRepository).deleteById(playerId)
        verify(fixture.lobbyRepository, never()).save(any(Lobby::class.java))
        verify(fixture.lock).unlockAsync(anyLong())
        verifyNoInteractions(fixture.container, fixture.redisTemplate)
    }

    /**
     * 验证离开大厅时通过当前大厅索引定位大厅，并在非空大厅中移除玩家和删除玩家索引。
     */
    @Test
    fun leaveLobbyUsesMembershipAndDeletesMembership() = runTest {
        val fixture = createFixture(true)
        val playerId = 456L
        val lobby = Lobby(123, arrayListOf(111L, playerId))
        lobby.host = 111L
        stubMembershipUntilDeleted(fixture.playerLobbyRepository, PlayerLobby(playerId, 123))
        `when`(fixture.lobbyRepository.findById(123)).thenReturn(Optional.of(lobby))
        `when`(fixture.lobbyRepository.save(any(Lobby::class.java))).thenAnswer { it.getArgument(0) }

        fixture.lobbyService.leaveLobby(playerId)

        assertThat(lobby.players!!).containsExactly(111L)
        verify(fixture.lobbyRepository).save(lobby)
        verify(fixture.playerLobbyRepository).deleteById(playerId)
        verify(fixture.redisTemplate, timeout(5_000)).convertAndSend(eq("123"), argThat { message ->
            message is LobbyMessage &&
                message.type == LobbyMessageType.LEAVE &&
                message.data == playerId
        })
        verify(fixture.lock).unlockAsync(anyLong())
        verifyNoInteractions(fixture.container)
    }

    /**
     * 验证房主离开时先持久化房主迁移，再广播房主更新和离开消息。
     */
    @Test
    fun leaveLobbyPersistsHostTransferBeforePublishingMessages() = runTest {
        val fixture = createFixture(true)
        val playerId = 456L
        val lobby = Lobby(123, arrayListOf(playerId, 111L))
        lobby.host = playerId
        stubMembershipUntilDeleted(fixture.playerLobbyRepository, PlayerLobby(playerId, 123))
        `when`(fixture.lobbyRepository.findById(123)).thenReturn(Optional.of(lobby))
        `when`(fixture.lobbyRepository.save(any(Lobby::class.java))).thenAnswer { it.getArgument(0) }

        fixture.lobbyService.leaveLobby(playerId)

        assertThat(lobby.host).isEqualTo(111L)
        Mockito.inOrder(fixture.lobbyRepository, fixture.playerLobbyRepository, fixture.redisTemplate, fixture.lock).apply {
            verify(fixture.lobbyRepository).save(argThat { savedLobby ->
                savedLobby.host == 111L && savedLobby.players == arrayListOf(111L)
            })
            verify(fixture.playerLobbyRepository).deleteById(playerId)
            verify(fixture.redisTemplate).convertAndSend(eq("123"), argThat { message ->
                message is LobbyMessage &&
                    message.type == LobbyMessageType.UPDATE_HOST &&
                    message.data == 111L
            })
            verify(fixture.redisTemplate).convertAndSend(eq("123"), argThat { message ->
                message is LobbyMessage &&
                    message.type == LobbyMessageType.LEAVE &&
                    message.data == playerId
            })
            verify(fixture.lock).unlockAsync(anyLong())
        }
        verifyNoInteractions(fixture.container)
    }

    /**
     * 验证房主持久化失败时不广播房主迁移，避免客户端提前接受未落库的新房主。
     */
    @Test
    fun leaveLobbyDoesNotPublishHostUpdateWhenHostTransferSaveFails() = runTest {
        val fixture = createFixture(true)
        val playerId = 456L
        val lobby = Lobby(123, arrayListOf(playerId, 111L))
        lobby.host = playerId
        stubMembershipUntilDeleted(fixture.playerLobbyRepository, PlayerLobby(playerId, 123))
        `when`(fixture.lobbyRepository.findById(123)).thenReturn(Optional.of(lobby))
        `when`(fixture.lobbyRepository.save(any(Lobby::class.java))).thenThrow(IllegalStateException("save failed"))

        assertFailsWith<IllegalStateException> {
            fixture.lobbyService.leaveLobby(playerId)
        }

        assertThat(lobby.host).isEqualTo(111L)
        verify(fixture.lobbyRepository).save(lobby)
        verify(fixture.playerLobbyRepository, never()).deleteById(playerId)
        verify(fixture.lock).unlockAsync(anyLong())
        verifyNoInteractions(fixture.redisTemplate, fixture.container)
    }

    /**
     * 验证 WebSocket 订阅只在玩家已属于大厅时注册监听，并在监听后发送当前大厅快照。
     */
    @Test
    fun subscribeLobbyRegistersListenerAndSendsLobbySnapshot() = runTest {
        val fixture = createFixture(true)
        val events = ArrayList<String>()
        val sentMessages = ArrayList<Any>()
        val player = LobbyPlayer(456L) {
            events.add("snapshot")
            sentMessages.add(it)
        }
        val lobby = Lobby(123, arrayListOf(111L, 456L))
        lobby.host = 111L
        `when`(fixture.lobbyRepository.findById(123)).thenReturn(Optional.of(lobby))
        Mockito.doAnswer {
            events.add("listener:${player.lobbyId}")
            null
        }.`when`(fixture.container).addMessageListener(eq(player.msgListener), any(PatternTopic::class.java))

        val subscribed = fixture.lobbyService.subscribeLobby(player, 123)

        assertThat(subscribed).isTrue()
        assertThat(player.lobbyId).isEqualTo(123)
        assertThat(events).containsExactly("listener:123", "snapshot")
        assertThat(sentMessages)
            .anySatisfy { message ->
                assertThat(message).isInstanceOf(LobbyMessage::class.java)
                assertThat((message as LobbyMessage).type).isEqualTo(LobbyMessageType.LOBBY_SNAPSHOT)
                assertThat(message.data).isSameAs(lobby)
            }
        verify(fixture.redissonClient).getLock("LobbyLock:123")
        verify(fixture.container).addMessageListener(eq(player.msgListener), any(PatternTopic::class.java))
        verify(fixture.lock).unlockAsync(anyLong())
        verifyNoInteractions(fixture.redisTemplate)
    }

    /**
     * 验证监听注册期间立即关闭连接时，也能按已记录的大厅 ID 移除 Redis 监听器。
     */
    @Test
    fun subscribeLobbyRemovesListenerWhenConnectionClosesDuringRegistration() = runTest {
        val fixture = createFixture(true)
        val player = LobbyPlayer(
            456L,
            {},
            { closedPlayer -> fixture.lobbyService.unsubscribeLobby(closedPlayer) },
        )
        val lobby = Lobby(123, arrayListOf(111L, 456L))
        lobby.host = 111L
        `when`(fixture.lobbyRepository.findById(123)).thenReturn(Optional.of(lobby))
        Mockito.doAnswer {
            player.closeConnection()
            null
        }.`when`(fixture.container).addMessageListener(eq(player.msgListener), any(PatternTopic::class.java))

        val subscribed = fixture.lobbyService.subscribeLobby(player, 123)

        assertThat(subscribed).isTrue()
        assertThat(player.lobbyId).isNull()
        verify(fixture.container).addMessageListener(eq(player.msgListener), any(PatternTopic::class.java))
        verify(fixture.container).removeMessageListener(
            eq(player.msgListener),
            eq(PatternTopic("123")),
        )
        verifyNoInteractions(fixture.redisTemplate)
    }

    /**
     * 验证取消 WebSocket 订阅只移除监听器，不再承担 HTTP 离开大厅的状态删除职责。
     */
    @Test
    fun unsubscribeLobbyRemovesListenerOnly() {
        val fixture = createFixture(true)
        val player = LobbyPlayer(456L) {}
        player.lobbyId = 123

        fixture.lobbyService.unsubscribeLobby(player)

        assertThat(player.lobbyId).isNull()
        verify(fixture.container).removeMessageListener(eq(player.msgListener), any(PatternTopic::class.java))
        verifyNoInteractions(
            fixture.lobbyRepository,
            fixture.redisTemplate,
            fixture.redissonClient,
            fixture.lock,
        )
    }

    /**
     * 验证收到自己的离开事件时，当前连接只执行关闭回调，不再把旧大厅消息转发给客户端。
     */
    @Test
    fun lobbyListenerClosesConnectionWhenOwnLeaveMessageArrives() {
        val sentMessages = ArrayList<Any>()
        val closeCount = AtomicInteger()
        val player = LobbyPlayer(
            456L,
            { sentMessages.add(it) },
            { _ -> closeCount.incrementAndGet() },
        )

        player.msgListener.onMessage(
            redisMessage(LobbyMessage(LobbyMessageType.LEAVE).apply {
                data = 456L
            }),
            null,
        )

        assertThat(sentMessages).isEmpty()
        assertThat(closeCount.get()).isEqualTo(1)
    }

    /**
     * 验证收到其他玩家的离开事件时，当前连接保持打开并正常转发消息。
     */
    @Test
    fun lobbyListenerForwardsOtherPlayerLeaveMessage() {
        val sentMessages = ArrayList<Any>()
        val closeCount = AtomicInteger()
        val player = LobbyPlayer(
            456L,
            { sentMessages.add(it) },
            { _ -> closeCount.incrementAndGet() },
        )

        player.msgListener.onMessage(
            redisMessage(LobbyMessage(LobbyMessageType.LEAVE).apply {
                data = 111L
            }),
            null,
        )

        assertThat(closeCount.get()).isZero()
        val message = sentMessages.single()
        assertThat(message).isInstanceOf(Map::class.java)
        val lobbyMessage = message as Map<*, *>
        assertThat(lobbyMessage["type"]).isEqualTo(LobbyMessageType.LEAVE.name)
        assertThat((lobbyMessage["data"] as Number).toLong()).isEqualTo(111L)
    }

    /**
     * 验证大厅销毁事件会关闭所有仍订阅该频道的本机连接。
     */
    @Test
    fun lobbyListenerClosesConnectionWhenLobbyDestroyedMessageArrives() {
        val firstCloseCount = AtomicInteger()
        val secondCloseCount = AtomicInteger()
        val firstPlayer = LobbyPlayer(456L, {}, { _ -> firstCloseCount.incrementAndGet() })
        val secondPlayer = LobbyPlayer(111L, {}, { _ -> secondCloseCount.incrementAndGet() })
        val destroyedMessage = redisMessage(LobbyMessage(LobbyMessageType.LOBBY_DESTROYED))

        firstPlayer.msgListener.onMessage(destroyedMessage, null)
        secondPlayer.msgListener.onMessage(destroyedMessage, null)

        assertThat(firstCloseCount.get()).isEqualTo(1)
        assertThat(secondCloseCount.get()).isEqualTo(1)
    }

    /**
     * 验证发送文本消息时通过当前大厅索引定位广播频道，并保持业务约定的无锁读路径。
     */
    @Test
    fun sendTextMessageUsesMembershipLobby() = runTest {
        val fixture = createFixture(true)
        val playerId = 456L
        val lobby = Lobby(123, arrayListOf(111L, playerId))
        lobby.host = 111L
        `when`(fixture.playerLobbyRepository.findById(playerId))
            .thenReturn(Optional.of(PlayerLobby(playerId, 123)))
        `when`(fixture.lobbyRepository.findById(123)).thenReturn(Optional.of(lobby))

        fixture.lobbyService.sendTextMessage(playerId, "hello")

        verify(fixture.redisTemplate, timeout(5_000)).convertAndSend(eq("123"), argThat { message ->
            message is LobbyMessage &&
                message.type == LobbyMessageType.TEXTING
        })
        verifyNoInteractions(fixture.redissonClient, fixture.lock, fixture.container)
    }

    /**
     * 验证当前大厅快照不包含发送者时，不再广播文本消息。
     */
    @Test
    fun sendTextMessageFailsWhenLobbyDoesNotContainPlayer() = runTest {
        val fixture = createFixture(true)
        val playerId = 456L
        val lobby = Lobby(123, arrayListOf(111L))
        lobby.host = 111L
        `when`(fixture.playerLobbyRepository.findById(playerId))
            .thenReturn(Optional.of(PlayerLobby(playerId, 123)))
        `when`(fixture.lobbyRepository.findById(123)).thenReturn(Optional.of(lobby))

        assertFailsWith<LobbyNotExist> {
            fixture.lobbyService.sendTextMessage(playerId, "hello")
        }

        verify(fixture.lobbyRepository).findById(123)
        verifyNoInteractions(fixture.redisTemplate, fixture.redissonClient, fixture.lock, fixture.container)
    }

    /**
     * 验证没有当前大厅索引时发送消息会按大厅不存在处理。
     */
    @Test
    fun sendTextMessageFailsWithoutMembership() = runTest {
        val fixture = createFixture(true)

        assertFailsWith<LobbyNotExist> {
            fixture.lobbyService.sendTextMessage(456L, "hello")
        }

        verifyNoInteractions(fixture.lobbyRepository, fixture.redisTemplate, fixture.redissonClient, fixture.lock, fixture.container)
    }

    /**
     * 创建大厅服务测试夹具并按顺序配置锁获取结果。
     *
     * @param lockResults 每次获取锁时返回的结果
     * @return 包含被测服务和依赖 mock 的测试夹具
     */
    private fun createFixture(vararg lockResults: Boolean): Fixture {
        val lobbyRepository = Mockito.mock(LobbyRepository::class.java)
        val playerLobbyRepository = Mockito.mock(PlayerLobbyRepository::class.java)
        @Suppress("UNCHECKED_CAST")
        val redisTemplate = Mockito.mock(RedisTemplate::class.java) as RedisTemplate<String, Any>
        val redissonClient = Mockito.mock(RedissonClient::class.java)
        val lock = Mockito.mock(RLock::class.java)
        val container = Mockito.mock(RedisMessageListenerContainer::class.java)
        `when`(redissonClient.getLock(anyString())).thenReturn(lock)
        `when`(redissonClient.getMultiLock(any(RLock::class.java), any(RLock::class.java))).thenReturn(lock)
        `when`(playerLobbyRepository.findById(any(Number::class.java))).thenReturn(Optional.empty())
        stubLock(lock, *lockResults)
        return Fixture(
            LobbyLifecycleService(lobbyRepository, playerLobbyRepository, redisTemplate, redissonClient, container),
            lobbyRepository,
            playerLobbyRepository,
            redisTemplate,
            redissonClient,
            lock,
            container,
        )
    }

    /**
     * 配置 Redisson 锁的异步获取和释放行为。
     *
     * @param lock 需要配置的 Redisson 锁 mock
     * @param lockResults 每次获取锁时返回的结果
     */
    private fun stubLock(lock: RLock, vararg lockResults: Boolean) {
        val futures = lockResults.map { completedFuture(it) }
        @Suppress("UNCHECKED_CAST")
        val unlockFuture = completedFuture(null) as RFuture<Void>
        `when`(
            lock.tryLockAsync(
                eq(LOCK_WAIT_MILLIS),
                eq(LOCK_WATCHDOG_LEASE_MILLIS),
                eq(TimeUnit.MILLISECONDS),
                anyLong(),
            ),
        ).thenReturn(futures.first(), *futures.drop(1).toTypedArray())
        `when`(lock.unlockAsync(anyLong())).thenReturn(unlockFuture)
    }

    /**
     * 模拟玩家索引被删除后的 Repository 状态，避免 leaveLobby 的退出确认读到旧 mock 数据。
     *
     * @param repository 玩家大厅索引 Repository mock
     * @param playerLobby 删除前存在的玩家索引
     */
    private fun stubMembershipUntilDeleted(repository: PlayerLobbyRepository, playerLobby: PlayerLobby) {
        val membership = AtomicReference(Optional.of(playerLobby))
        `when`(repository.findById(playerLobby.playerId)).thenAnswer { membership.get() }
        Mockito.doAnswer {
            membership.set(Optional.empty())
            null
        }.`when`(repository).deleteById(playerLobby.playerId)
    }

    /**
     * 使用项目 Redis 序列化器构造监听器收到的原始消息。
     *
     * @param payload 需要写入 Redis 消息体的对象
     * @return Redis 原始消息 mock
     */
    private fun redisMessage(payload: Any): Message {
        val message = Mockito.mock(Message::class.java)
        `when`(message.body).thenReturn(RedisConfiguration.SERIALIZER.serialize(payload))
        return message
    }

    /**
     * 创建立即完成的 Redisson 异步结果。
     *
     * @param value 异步结果值
     * @return 立即回调完成的 RFuture mock
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T> completedFuture(value: T): RFuture<T> {
        val future = Mockito.mock(RFuture::class.java) as RFuture<T>
        `when`(future.whenComplete(any())).thenAnswer { invocation ->
            val consumer = invocation.getArgument<BiConsumer<T, Throwable?>>(0)
            consumer.accept(value, null)
            future
        }
        return future
    }

    private data class Fixture(
        val lobbyService: LobbyLifecycleService,
        val lobbyRepository: LobbyRepository,
        val playerLobbyRepository: PlayerLobbyRepository,
        val redisTemplate: RedisTemplate<String, Any>,
        val redissonClient: RedissonClient,
        val lock: RLock,
        val container: RedisMessageListenerContainer,
    )
}

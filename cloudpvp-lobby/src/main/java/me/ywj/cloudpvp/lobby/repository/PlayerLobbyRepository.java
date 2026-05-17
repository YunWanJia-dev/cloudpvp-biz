package me.ywj.cloudpvp.lobby.repository;

import me.ywj.cloudpvp.lobby.entity.PlayerLobby;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

/**
 * PlayerLobbyRepository
 * 玩家当前大厅索引仓库。
 *
 * @author sheip9
 * @since 2026/5/17 17:27
 */
@Repository
public interface PlayerLobbyRepository extends CrudRepository<PlayerLobby, Number> {
}

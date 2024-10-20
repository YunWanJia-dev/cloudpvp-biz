package me.ywj.cloudpvp.lobby.repository;

import me.ywj.cloudpvp.lobby.entity.Lobby;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

/**
 * LobbyRepository
 *
 * @author sheip9
 * @since 2024/10/20 16:42
 */
@Repository
public interface LobbyRepository extends CrudRepository<Lobby, Integer> {
}

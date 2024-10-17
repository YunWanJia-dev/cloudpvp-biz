package me.ywj.cloudpvp.cloudpvpstate.repository;

import me.ywj.cloudpvp.cloudpvpstate.entity.PlayerState;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

/**
 * PlayerStateRepository
 *
 * @author sheip9
 * @since 2024/10/17 11:59
 */
@Repository
public interface PlayerStateRepository extends CrudRepository<PlayerState, Number> {
}

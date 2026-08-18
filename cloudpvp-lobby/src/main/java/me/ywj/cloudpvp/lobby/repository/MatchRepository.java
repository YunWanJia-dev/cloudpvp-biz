package me.ywj.cloudpvp.lobby.repository;

import me.ywj.cloudpvp.lobby.entity.Match;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

/**
 * MatchRepository
 * 比赛状态的 Redis 仓库。
 *
 * @author sheip9
 * @since 2026/8/17 17:43
 */
@Repository
public interface MatchRepository extends CrudRepository<Match, String> {
}

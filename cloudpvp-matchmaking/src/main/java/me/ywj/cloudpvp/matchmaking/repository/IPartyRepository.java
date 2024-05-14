package me.ywj.cloudpvp.matchmaking.repository;

import me.ywj.cloudpvp.matchmaking.entity.Party;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IPartyRepository extends CrudRepository<Party, String> {
   
}

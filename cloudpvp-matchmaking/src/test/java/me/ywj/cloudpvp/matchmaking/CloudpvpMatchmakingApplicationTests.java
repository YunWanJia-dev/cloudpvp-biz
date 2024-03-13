package me.ywj.cloudpvp.matchmaking;

import me.ywj.cloudpvp.matchmaking.service.IPartyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class CloudpvpMatchmakingApplicationTests {

	@Test
	void contextLoads() {
	}
	@Autowired
	IPartyService partyService;
	@Test
	void testRedis(){
		partyService.joinParty("1", "1");
	}

}

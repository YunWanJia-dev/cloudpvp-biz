package me.ywj.cloudpvp.matchmaking;

import me.ywj.cloudpvp.matchmaking.constants.PartyActionEnum;
import me.ywj.cloudpvp.matchmaking.model.PartyPayload;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class CloudpvpMatchmakingApplicationTests {

	@Test
	void contextLoads() {
	}
	@Test
	void testGson () {
		PartyPayload payload = new PartyPayload();
		payload.setAction(PartyActionEnum.JOIN_PARTY);
		payload.setContent("1");
	}
}
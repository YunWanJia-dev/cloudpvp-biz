package me.ywj.cloudpvp.play;

import me.ywj.cloudpvp.play.service.GameService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "apollo.bootstrap.enabled=false",
        "apollo.bootstrap.eagerLoad.enabled=false",
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
class CloudpvpPlayApplicationTests {
    @Autowired
    private GameService gameService;

    @Test
    void contextLoads() {}

    @Test
    void loadsPlayDataFromConfiguration() {
        assertThat(gameService.getGames())
                .extracting("key")
                .containsExactly("CS2", "CSGO");
        assertThat(gameService.getModes("CS2"))
                .extracting("key")
                .containsExactly("TEST-CS2");
        assertThat(gameService.getModes("UNKNOWN")).isEmpty();
    }
}

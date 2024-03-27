package me.ywj.cloudpvp.matchmaking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;

/**
 * @author sheip9
 */
@SpringBootApplication
@EnableDiscoveryClient
public class CloudpvpMatchmakingApplication {

	public static void main(String[] args) {
		SpringApplication.run(CloudpvpMatchmakingApplication.class, args);
	}

}

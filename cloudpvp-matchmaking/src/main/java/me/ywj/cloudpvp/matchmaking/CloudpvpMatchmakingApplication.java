package me.ywj.cloudpvp.matchmaking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * CloudpvpMatchmakingApplication
 * 原匹配&大厅管理
 * 弃用，待重构
 * @author sheip9
 */
@SpringBootApplication
@EnableDiscoveryClient
@Deprecated
public class CloudpvpMatchmakingApplication {
	@Deprecated
	public static void main(String[] args) {
		SpringApplication.run(CloudpvpMatchmakingApplication.class, args);
	}

}

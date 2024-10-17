package me.ywj.cloudpvp.center;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * CloudpvpCenterApplication 
 * 注册中心模块
 * 
 * @author sheip9 
 * @since 2024/2/4 20:51
 */
@SpringBootApplication
@EnableEurekaServer
@EnableConfigServer
public class CloudpvpCenterApplication {

	public static void main(String[] args) {
		SpringApplication.run(CloudpvpCenterApplication.class, args);
	}

}

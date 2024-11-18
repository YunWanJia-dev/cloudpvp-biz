package me.ywj.cloudpvp.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
//import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

/**
 * CloudpvpAuthApplication
 * 鉴权与Token颁发模块
 * 
 * @author sheip9
 * @since 2024/1/21 12:30
 */
@SpringBootApplication
@EnableDiscoveryClient
//@EnableWebSecurity
public class CloudpvpAuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(CloudpvpAuthApplication.class, args);
    }

}

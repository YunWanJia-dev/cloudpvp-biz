package me.ywj.cloudpvp.auth;

import me.ywj.cloudpvp.beans.ModuleInfo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
//import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

/**
 * CloudpvpAuthApplication
 * 鉴权与Token颁发模块
 *
 * @author sheip9
 * @since 2024/1/21 12:30
 */
@SpringBootApplication(scanBasePackages = "me.ywj.cloudpvp")
@EnableDiscoveryClient
@Configuration
public class CloudpvpAuthApplication {
    public static void main(String[] args) {
        SpringApplication.run(CloudpvpAuthApplication.class, args);
    }

    @Bean
    public ModuleInfo moduleInfo() {
        return () -> "鉴权模块";
    }

}

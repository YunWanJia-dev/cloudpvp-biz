package me.ywj.cloudpvp.lobby;

import me.ywj.cloudpvp.beans.ModuleInfo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * CloudpvpLobbyApplication
 * 大厅管理和状态模块
 *
 * @author sheip9
 * @since 2024/10/17 16:34
 */
@SpringBootApplication(scanBasePackages = "me.ywj.cloudpvp")
@EnableDiscoveryClient
@Configuration
public class CloudpvpLobbyApplication {

    public static void main(String[] args) {
        SpringApplication.run(CloudpvpLobbyApplication.class, args);
    }

    @Bean
    public ModuleInfo moduleInfo() {
        return () -> "大厅模块";
    }

}

package me.ywj.cloudpvp.state;

import me.ywj.cloudpvp.beans.ModuleInfo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * CloudpvpStateApplication
 * 用户状态与事件推送模块
 *
 * @author sheip9
 * @since 2024/10/17 16:34
 */
@SpringBootApplication(scanBasePackages = "me.ywj.cloudpvp")
@EnableDiscoveryClient
@Configuration
public class CloudpvpStateApplication {

    public static void main(String[] args) {
        SpringApplication.run(CloudpvpStateApplication.class, args);
    }

    @Bean
    public ModuleInfo moduleInfo() {
        return () -> "用户状态模块";
    }

}

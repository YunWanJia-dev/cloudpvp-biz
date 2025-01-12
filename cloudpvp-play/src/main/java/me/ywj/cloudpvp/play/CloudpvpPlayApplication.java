package me.ywj.cloudpvp.play;

import me.ywj.cloudpvp.beans.ModuleInfo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * CloudpvpPlayApplication
 * 游戏与模式信息获取模块
 *
 * @author sheip9
 * @since 2024/5/14 19:48
 */
@SpringBootApplication(scanBasePackages = "me.ywj.cloudpvp")
@EnableDiscoveryClient
@Configuration
public class CloudpvpPlayApplication {

    public static void main(String[] args) {
        SpringApplication.run(CloudpvpPlayApplication.class, args);
    }

    @Bean
    public ModuleInfo moduleInfo() {
        return () -> "游玩信息模块";
    }

}

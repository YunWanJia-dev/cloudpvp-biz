package me.ywj.cloudpvp.usersummary;


import me.ywj.cloudpvp.beans.ModuleInfo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * CloudPVPUserSummaryApplication
 * 用户信息获取模块
 *
 * @author sheip9
 * @since 2024/6/6  17:18
 */
@SpringBootApplication(scanBasePackages = "me.ywj.cloudpvp")
@EnableDiscoveryClient
@Configuration
public class CloudpvpUserSummaryApplication {

    public static void main(String[] args) {
        SpringApplication.run(CloudpvpUserSummaryApplication.class, args);
    }

    @Bean
    public ModuleInfo moduleInfo() {
        return () -> "用户信息模块";
    }

}

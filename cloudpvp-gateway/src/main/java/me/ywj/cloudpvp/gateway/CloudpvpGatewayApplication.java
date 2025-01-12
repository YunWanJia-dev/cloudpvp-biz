package me.ywj.cloudpvp.gateway;

import me.ywj.cloudpvp.beans.ModuleInfo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * CloudpvpGatewayApplication
 * 网关模块
 *
 * @author sheip9
 * @since 2024/5/14 19:48
 */
@SpringBootApplication(scanBasePackages = "me.ywj.cloudpvp")
@EnableDiscoveryClient
@Configuration
public class CloudpvpGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(CloudpvpGatewayApplication.class, args);
    }

    @Bean
    public ModuleInfo moduleInfo() {
        return () -> "网关模块";
    }

}

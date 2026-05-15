package me.ywj.cloudpvp.auth;

import me.ywj.cloudpvp.auth.service.SteamAuthService;
import me.ywj.cloudpvp.core.service.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CloudpvpAuthApplicationTests
 * 鉴权模块上下文测试类
 *
 * @author sheip9
 * @since 2026/5/15 13:54
 */
@SpringBootTest(properties = {
        "apollo.bootstrap.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "eureka.client.enabled=false",
        "cloudpvp.jwt.secret=test-secret",
        "cloudpvp.jwt.expire-time=60000"
})
class CloudpvpAuthApplicationTests {
    @Autowired
    private TokenService tokenService;

    @Autowired
    private SteamAuthService steamAuthService;

    private Field tokenServiceField;

    /**
     * setUp
     * 初始化测试需要访问的反射字段。
     *
     * @throws Exception 反射字段不存在时抛出
     */
    @BeforeEach
    void setUp() throws Exception {
        tokenServiceField = SteamAuthService.class.getDeclaredField("tokenService");
        tokenServiceField.setAccessible(true);
    }

    /**
     * contextLoads
     * 验证鉴权模块 Spring 上下文可以启动。
     */
    @Test
    void contextLoads() {
    }

    /**
     * steamAuthServiceDependsOnTokenServiceInterface
     * 验证 SteamAuthService 注入的是 TokenService 接口 bean。
     *
     * @throws Exception 反射读取字段失败时抛出
     */
    @Test
    void steamAuthServiceDependsOnTokenServiceInterface() throws Exception {
        assertThat(tokenServiceField.getType()).isEqualTo(TokenService.class);
        assertThat(tokenServiceField.get(steamAuthService)).isSameAs(tokenService);
    }
}

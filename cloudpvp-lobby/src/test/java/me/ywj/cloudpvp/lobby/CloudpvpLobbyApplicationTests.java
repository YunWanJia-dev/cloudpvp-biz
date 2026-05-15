package me.ywj.cloudpvp.lobby;

import me.ywj.cloudpvp.core.service.TokenService;
import me.ywj.cloudpvp.lobby.configure.WebsocketConfigure;
import me.ywj.cloudpvp.lobby.interceptor.IdInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CloudpvpLobbyApplicationTests
 * 大厅模块上下文测试类
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
class CloudpvpLobbyApplicationTests {
    @Autowired
    private TokenService tokenService;

    @Autowired
    private IdInterceptor idInterceptor;

    @Autowired
    private WebsocketConfigure websocketConfigure;

    private Field idInterceptorTokenServiceField;
    private Field websocketIdInterceptorField;

    /**
     * setUp
     * 初始化测试需要访问的反射字段。
     *
     * @throws Exception 反射字段不存在时抛出
     */
    @BeforeEach
    void setUp() throws Exception {
        idInterceptorTokenServiceField = IdInterceptor.class.getDeclaredField("tokenService");
        idInterceptorTokenServiceField.setAccessible(true);
        websocketIdInterceptorField = WebsocketConfigure.class.getDeclaredField("idInterceptor");
        websocketIdInterceptorField.setAccessible(true);
    }

    /**
     * contextLoads
     * 验证大厅模块 Spring 上下文可以启动。
     */
    @Test
    void contextLoads() {
    }

    /**
     * idInterceptorDependsOnTokenServiceInterface
     * 验证 IdInterceptor 注入的是 TokenService 接口 bean。
     *
     * @throws Exception 反射读取字段失败时抛出
     */
    @Test
    void idInterceptorDependsOnTokenServiceInterface() throws Exception {
        assertThat(idInterceptorTokenServiceField.getType()).isEqualTo(TokenService.class);
        assertThat(idInterceptorTokenServiceField.get(idInterceptor)).isSameAs(tokenService);
    }

    /**
     * websocketConfigureUsesSpringManagedIdInterceptor
     * 验证 websocket 配置使用的是 Spring 管理的 IdInterceptor。
     *
     * @throws Exception 反射读取字段失败时抛出
     */
    @Test
    void websocketConfigureUsesSpringManagedIdInterceptor() throws Exception {
        assertThat(websocketIdInterceptorField.get(websocketConfigure)).isSameAs(idInterceptor);
    }
}

package me.ywj.cloudpvp.gateway.filter;

import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.factory.PreserveHostHeaderGatewayFilterFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * EnablePreserveHostHeaderFilter
 *
 * @author sheip9
 * @since 2024/11/18 15:26
 */
@Component
@AllArgsConstructor(onConstructor = @__(@Autowired))
public class EnablePreserveHostHeaderFilter implements GlobalFilter {
    private final PreserveHostHeaderGatewayFilterFactory preserveHostHeaderGatewayFilterFactory;
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return preserveHostHeaderGatewayFilterFactory.apply().filter(exchange, chain);
    }
}

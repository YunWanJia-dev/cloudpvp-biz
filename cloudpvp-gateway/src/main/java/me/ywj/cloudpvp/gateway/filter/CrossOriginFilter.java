package me.ywj.cloudpvp.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * CrossOriginFilter
 *
 * @author sheip9
 * @since 2024/11/17 23:30
 */
@Component
public class CrossOriginFilter implements GlobalFilter{
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        exchange.getResponse().getHeaders().add("Access-Control-Allow-Origin", "*");
        return chain.filter(exchange);
    }
    
}

package me.ywj.cloudpvp.gateway.filter;

import me.ywj.cloudpvp.core.utils.TokenUtils;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public class TokenFilter implements GatewayFilter {
    private final TokenUtils tokenUtils = new TokenUtils();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String token = (String) exchange.getAttributes().get("token");
        if (!tokenUtils.validateToken(token)) {
            return null;
        }
        return chain.filter(exchange);
    }
}

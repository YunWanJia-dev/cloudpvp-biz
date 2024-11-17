package me.ywj.cloudpvp.gateway;

import io.netty.resolver.DefaultAddressResolverGroup;
import org.springframework.cloud.gateway.config.HttpClientCustomizer;
import org.springframework.stereotype.Component;
import reactor.netty.http.client.HttpClient;

/**
 * DnsResolutionFixer
 *
 * @author sheip9
 * @since 2024/11/17 23:28
 */
@Component
public class DnsResolutionFixer implements HttpClientCustomizer {
    @Override
    public HttpClient customize(HttpClient httpClient) {
        return httpClient.resolver(DefaultAddressResolverGroup.INSTANCE);
    }
}

package me.ywj.cloudpvp.core.utils;

import me.ywj.cloudpvp.core.model.http.HttpBase;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

/**
 * UriUtils
 *
 * @author sheip9
 * @since 2024/11/14 16:20
 */
public class HttpUtils {
    private final HttpClient httpClient;
    private final HttpBase httpBase;

    public HttpUtils(HttpBase base) {
        this.httpBase = base;
        this.httpClient = HttpClient.newHttpClient();
    }

    private static HttpRequest.Builder constructBaseRequest(String url,@Nullable Map<String, String> headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder();
        builder.uri(URI.create(url));
        if (headers != null && !headers.isEmpty()) {
            headers.forEach(builder::header);
        }
        return builder;
    }
    
    private HttpRequest.Builder constructRequest() {
        return constructBaseRequest(httpBase.getBaseUri(), httpBase.getHeader());
    }
    
    public static HttpResponse<String> post(String url, Object body) throws Exception {
        var request = constructBaseRequest(url, null).POST(HttpRequest.BodyPublishers.ofString(JacksonUtils.serialize(body))).build();
        try(var client = HttpClient.newHttpClient()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    public static HttpResponse<String> post(String url) throws Exception {
        return post(url, null);
    }

    public HttpResponse<String> post(Object body) throws Exception {
        var req = constructRequest().POST(HttpRequest.BodyPublishers.ofString(JacksonUtils.serialize(body))).build();
        return send(req);
    }
    
    private HttpResponse<String> send(HttpRequest request) throws Exception {
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}


package me.ywj.cloudpvp.core.utils;

import me.ywj.cloudpvp.core.model.http.HttpBase;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
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

    private HttpRequest.Builder constructRequest() {
        HttpRequest.Builder builder = HttpRequest.newBuilder();
        builder.uri(URI.create(httpBase.getBaseUri()));
        if(!httpBase.getHeader().isEmpty()) {
            httpBase.getHeader().forEach(builder::header);
        }
        return builder;
    }

    public HttpResponse<String> post(Object body) throws Exception {
        var req = constructRequest().POST(HttpRequest.BodyPublishers.ofString(JacksonUtils.serialize(body))).build();
        return send(req);
    }
    
    private HttpResponse<String> send(HttpRequest request) throws Exception {
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}


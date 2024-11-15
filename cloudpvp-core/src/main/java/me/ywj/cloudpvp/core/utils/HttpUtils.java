package me.ywj.cloudpvp.core.utils;

import me.ywj.cloudpvp.core.model.configure.HttpConfigure;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * UriUtils
 *
 * @author sheip9
 * @since 2024/11/14 16:20
 */
public class HttpUtils {
    private final String baseUri;
    private final HttpClient httpClient;
    private final HttpRequest.Builder requestBuilder;

    public HttpUtils(HttpConfigure base) {
        this.baseUri = base.getBaseUri(); 
        this.httpClient = constructHttpClient(base);
        this.requestBuilder = constructRequest(base);
    }

    private static HttpRequest.Builder constructRequest(HttpConfigure base) {
        HttpRequest.Builder builder = HttpRequest.newBuilder();
        if (base.getHeader() != null && !base.getHeader().isEmpty()) {
            base.getHeader().forEach(builder::header);
        }
        return builder; 
    }
    
    public static HttpClient constructHttpClient(HttpConfigure base) {
        return HttpClient.newHttpClient();
    }
    
    private HttpRequest.Builder newRequest(String path) {
        return requestBuilder.copy().uri(URI.create(baseUri + path));
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        return this.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    public HttpResponse<String> get(String path) throws Exception {
        var req = newRequest(path).GET().build();
        return send(req);
    }

    public HttpResponse<String> get() throws Exception {
        return get("");
    }

    public HttpResponse<String> post(String path, Object body) throws Exception {
        var req = newRequest(path).POST(HttpRequest.BodyPublishers.ofString(JacksonUtils.serialize(body))).build();
        return send(req);
    }

    public HttpResponse<String> post(Object body) throws Exception {
        return post("", body);
    }

    public HttpResponse<String> post() throws Exception {
        return post("", null);
    }
}


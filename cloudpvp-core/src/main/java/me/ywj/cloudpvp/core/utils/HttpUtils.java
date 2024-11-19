package me.ywj.cloudpvp.core.utils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * HttpUtils
 *  网络请求工具类
 * @author sheip9
 * @since 2024/11/14 16:20
 */
public class HttpUtils {
    private final HttpClient httpClient;
    private final HttpRequest baseRequest;
    
    public HttpUtils(HttpRequest baseRequest) {
        this.httpClient = HttpClient.newHttpClient();
        this.baseRequest = baseRequest;
    }
    
    private HttpRequest.Builder newRequest(String path) {
        return HttpRequest.newBuilder(baseRequest, (s1, s2) -> false).uri(URI.create(baseRequest.uri().toString() + path));
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        return this.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * 发起GET请求
     * @param path 路径
     * @return HttpResponse
     * @throws Exception 异常
     */
    public HttpResponse<String> get(String path) throws Exception {
        var req = newRequest(path).GET().build();
        return send(req);
    }

    /**
     * 发起GET请求
     * @return HttpResponse
     * @throws Exception 异常
     */
    public HttpResponse<String> get() throws Exception {
        return get("");
    }

    /**
     * 发起POST请求
     * @param path 路径
     * @param body 内容体
     * @return HttpResponse
     * @throws Exception 异常
     */
    public HttpResponse<String> post(String path, Object body) throws Exception {
        var req = newRequest(path).POST(HttpRequest.BodyPublishers.ofString(JacksonUtils.serialize(body))).build();
        return send(req);
    }

    /**
     * 发起POST请求
     * @param body 内容体
     * @return HttpResponse
     * @throws Exception 异常
     */
    public HttpResponse<String> post(Object body) throws Exception {
        return post("", body);
    }

    /**
     * 发起POST请求
     * @return HttpResponse
     * @throws Exception 异常
     */
    public HttpResponse<String> post() throws Exception {
        return post("", null);
    }
}


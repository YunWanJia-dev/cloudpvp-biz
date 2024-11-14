package me.ywj.cloudpvp.core.model.http;

import java.util.Map;

public class HttpBase {
    private String baseUri;
    private Map<String, String> header;

    public HttpBase(String baseUri, Map<String, String> header) {
        this.baseUri = baseUri;
        this.header = header;
    }

    public String getBaseUri() {
        return baseUri;
    }

    public Map<String, String> getHeader() {
        return header;
    }
}

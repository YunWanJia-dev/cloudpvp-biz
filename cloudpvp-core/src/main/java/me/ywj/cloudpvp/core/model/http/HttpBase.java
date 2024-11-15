package me.ywj.cloudpvp.core.model.http;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
@Builder
public class HttpBase {
    private String baseUri;
    private Map<String, String> header;

    public HttpBase(String baseUri, Map<String, String> header) {
        this.baseUri = baseUri;
        this.header = header;
    }

}

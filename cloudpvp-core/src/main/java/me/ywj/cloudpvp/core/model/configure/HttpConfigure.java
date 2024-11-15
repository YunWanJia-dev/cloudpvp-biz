package me.ywj.cloudpvp.core.model.configure;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

/**
 * HttpBase
 * @author sheip9
 */
@Getter
@Builder
public class HttpConfigure {
    // HttpRequest setting start
    private String baseUri;
    private Map<String, String> header;
    // HttpRequest setting end
}

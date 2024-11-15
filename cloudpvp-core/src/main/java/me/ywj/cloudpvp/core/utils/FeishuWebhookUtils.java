package me.ywj.cloudpvp.core.utils;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import me.ywj.cloudpvp.core.model.configure.HttpConfigure;
import me.ywj.cloudpvp.core.model.configure.FeishuWebhookConfigure;

import java.io.Serializable;
import java.net.URISyntaxException;

/**
 * FartWebhookUtils
 *
 * @author sheip9
 * @since 2024/11/14 16:19
 */
public class FeishuWebhookUtils {

    private final HttpUtils httpUtils;

    public FeishuWebhookUtils(FeishuWebhookConfigure configure) throws URISyntaxException {
        this.httpUtils = new HttpUtils(
                HttpConfigure.builder()
                .baseUri(configure.getUri())
                .build()
        );
    }

    public void send(String text) throws Exception {
        var resp = httpUtils.post(new MessageBody(text));
        System.out.println(resp.body());
    }
}

@Getter
class MessageBody implements Serializable {
    @JsonProperty("msg_type")
    final String MsgType = "text";
    final MessageContent content;

    public MessageBody(String content) {
        this.content = new MessageContent(content);
    }

}

@Getter
class MessageContent implements Serializable {
    String text;

    public MessageContent(String text) {
        this.text = text;
    }

}
package me.ywj.cloudpvp.core.utils;

import com.fasterxml.jackson.annotation.JsonProperty;
import me.ywj.cloudpvp.core.model.http.HttpBase;
import me.ywj.cloudpvp.core.model.webhook.FeishuWebhookConfigure;

import java.io.IOException;
import java.io.Serializable;
import java.net.URISyntaxException;
import java.util.HashMap;

/**
 * FartWebhookUtils
 *
 * @author sheip9
 * @since 2024/11/14 16:19
 */
public class FeishuWebhookUtils {
    private final FeishuWebhookConfigure configure;
    
    private final HttpUtils httpUtils;

    public FeishuWebhookUtils(FeishuWebhookConfigure configure) throws URISyntaxException {
        this.configure = configure;
        this.httpUtils = new HttpUtils(new HttpBase(configure.getUrl(), new HashMap<>())); 
    }
    
    public void send(String text) throws Exception {
        var resp = httpUtils.post(new MessageBody(text));
        System.out.println(resp.body());
    }
}

class MessageBody implements Serializable {
    @JsonProperty("msg_type")
    final String MsgType = "text";
    @JsonProperty("content")
    final MessageContent content;

    public MessageBody(String content) {
        this.content = new MessageContent(content);
    }

    public String getMsgType() {
        return MsgType;
    }

    public MessageContent getContent() {
        return content;
    }
}

class MessageContent implements Serializable {
    String text;

    public MessageContent(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }
}
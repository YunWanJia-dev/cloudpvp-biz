package me.ywj.cloudpvp.core.utils;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import me.ywj.cloudpvp.core.model.configure.FeishuWebhookConfigure;

import java.net.URI;
import java.net.http.HttpRequest;

/**
 * FartWebhookUtils
 * 飞书webhook机器人工具类
 *
 * @author sheip9
 * @since 2024/11/14 16:19
 */
public class FeishuWebhookUtils {
    private final HttpUtils httpUtils;

    public FeishuWebhookUtils(FeishuWebhookConfigure configure) {
        this.httpUtils = new HttpUtils(
                HttpRequest.newBuilder()
                        .uri(URI.create(configure.getUri()))
                        .build()
        );
    }

    /**
     * send
     * 发送消息
     *
     * @param text 消息文本
     * @return 是否发送成功
     */
    public boolean send(String text) {
        try {
            var resp = httpUtils.post(MessageBody.text(text));
            var body = JacksonUtils.deserialize(resp.body(), FeishuResponse.class);
            return body.code() == 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

}

@Getter
class MessageBody {
    @JsonProperty("msg_type")
    private final String MsgType;
    private final MessageContent content;

    public MessageBody(String msgType, String content) {
        this.MsgType = msgType;
        this.content = new MessageContent(content);
    }

    public static MessageBody text(String content) {
        return new MessageBody("text", content);
    }

}

record MessageContent(String text) {
}

record FeishuResponse(Integer code, String msg) {
}
package me.ywj.cloudpvp.core.utils;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.module.kotlin.KotlinModule;

import java.io.IOException;

/**
 * JacksonUtils
 * 序列化工具类
 *
 * @author sheip9
 * @since 2024/10/25 16:26
 */
public class JacksonUtils {
    /**
     * Jackson实例
     */
    public final static ObjectMapper INSTANCE = createInstance();

    private JacksonUtils() {
    }

    /**
     * 创建和设定实例
     */
    private static ObjectMapper createInstance() {
        ObjectMapper objectMapper = new ObjectMapper();
        // core 里存在 Kotlin data class，注册 Kotlin 模块后 Jackson 才能可靠识别主构造器和空值语义。
        objectMapper.registerModule(new KotlinModule.Builder().build());
        objectMapper.configure(JsonParser.Feature.INCLUDE_SOURCE_IN_LOCATION, true);
        objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return objectMapper;
    }

    /**
     * 序列化对象
     *
     * @param o 要序列化的对象
     * @return 序列化后的JSON字符串
     * @throws IOException 异常
     */
    public static String serialize(Object o) throws IOException {
        return INSTANCE.writeValueAsString(o);
    }

    /**
     * 反序列化对象
     *
     * @param json  要用于反序列化的JSON字符串
     * @param clazz 反序列化的目标对象
     * @return 反序列化后的对象
     * @throws IOException 异常
     */
    public static <T> T deserialize(String json, Class<T> clazz) throws IOException {
        return INSTANCE.readValue(json, clazz);
    }

    /**
     * 反序列化带泛型的对象
     *
     * @param json          要用于反序列化的JSON字符串
     * @param typeReference 反序列化的目标类型
     * @return 反序列化后的对象
     * @throws IOException 异常
     */
    public static <T> T deserialize(String json, TypeReference<T> typeReference) throws IOException {
        // SteamResponse<T> 这类包装结构需要保留泛型信息，Class<T> 无法表达内部类型。
        return INSTANCE.readValue(json, typeReference);
    }
}

package me.ywj.cloudpvp.core.utils;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

/**
 * JacksonUtils
 *
 * @author sheip9
 * @since 2024/10/25 16:26
 */
public class JacksonUtils {
    public static ObjectMapper INSTANCE = createInstance();
    
    private JacksonUtils() {}
    
    private static ObjectMapper createInstance() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(JsonParser.Feature.INCLUDE_SOURCE_IN_LOCATION, true);
        return objectMapper;
    }
    
    public static <T> T deserializeFromBytes(byte[] bytes, Class<T> clazz) throws IOException {
        return INSTANCE.readValue(bytes, clazz);
    }
    
    public static String serialize(Object o) throws IOException {
        return INSTANCE.writeValueAsString(o);
    }
    
    public static String byteToJson(byte[] bytes) throws IOException {
        return deserializeFromBytes(bytes, String.class);
    }
}

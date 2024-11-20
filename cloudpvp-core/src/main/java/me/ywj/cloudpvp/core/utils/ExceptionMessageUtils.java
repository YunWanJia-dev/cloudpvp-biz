package me.ywj.cloudpvp.core.utils;

/**
 * ExceptionMessageUtils
 *
 * @author sheip9
 * @since 2024/11/20 14:10
 */
public class ExceptionMessageUtils {
    static final String MESSAGE_TEMPLATE = """
            \
            模块名：%s
            异常类：%s
            原因：%s
            触发语句：%s
            """;

    public static String getMessage(String moduleName, Exception e) {
        return String.format(
                MESSAGE_TEMPLATE,
                moduleName,
                e.getClass().getName(),
                e.getLocalizedMessage(),
                e.getStackTrace()[e.getStackTrace().length - 1]
        );
    }

}

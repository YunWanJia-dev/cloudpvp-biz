package me.ywj.cloudpvp.core.utils;

/**
 * ExceptionMessageUtils
 * 异常消息工具类
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
    static final String UNEXPECTED_EXCEPTION = "发生未捕获的异常：\n";
    static final String UNEXPECTED_EXIT = "发生了异常退出：\n";

    /**
     * formatMessage
     * 异常消息格式化
     *
     * @param moduleName 模块名
     * @param title      标题或第一行文字
     * @param e          异常
     * @return 使用模板格式化后的文本
     */
    public static String formatMessage(String moduleName, String title, Exception e) {
        return String.format(
                title + MESSAGE_TEMPLATE,
                moduleName,
                e.getClass().getName(),
                e.getLocalizedMessage(),
                e.getStackTrace()[e.getStackTrace().length - 1]
        );
    }

    /**
     * unexpectedExceptionMessage
     * 格式化未捕获异常消息
     *
     * @param moduleName 模块名
     * @param e          异常
     * @return 格式化后消息
     */
    public static String unexpectedExceptionMessage(String moduleName, Exception e) {
        return formatMessage(moduleName, UNEXPECTED_EXCEPTION, e);
    }

    /**
     * unexpectedExitMessage
     * 格式化异常退出消息
     *
     * @param moduleName 模块名
     * @param e          异常
     * @return 格式化后消息
     */
    public static String unexpectedExitMessage(String moduleName, Exception e) {
        return formatMessage(moduleName, UNEXPECTED_EXIT, e);
    }

}

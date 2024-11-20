package me.ywj.cloudpvp.core.utils;

import lombok.Synchronized;

import java.util.Random;

/**
 * RandomUtils
 * 随机数和字符串工具类
 *
 * @author sheip9
 * @since 2024/10/24 15:49
 */
public class RandomUtils {
    private final Random random;

    public RandomUtils() {
        random = new Random();
    }

    /**
     * buildRandomNumString
     * 生成给定长度的随机数字字符串
     *
     * @param length 长度
     * @return 生成结果
     */
    @Synchronized
    public String buildRandomNumString(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }

    /**
     * buildRandomLengthInteger
     * 生成给定长度的随机位数的整数数字
     *
     * @param length 长度
     * @return 生成结果
     */
    public int buildRandomLengthInteger(int length) {
        if (length > 10) {
            throw new IllegalArgumentException("length > 10");
        }
        return Integer.parseInt(buildRandomNumString(length));
    }

    /**
     * buildRandomLengthLong
     * 生成给定长度的随机位数的整数数字
     *
     * @param length 长度
     * @return 生成结果
     */
    public long buildRandomLengthLong(int length) {
        if (length > 19) {
            throw new IllegalArgumentException("length > 19");
        }
        return Long.parseLong(buildRandomNumString(length));
    }

}

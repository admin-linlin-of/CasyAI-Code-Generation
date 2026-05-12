package com.casy.casyaicodemother.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class commUtil {

    /**
     * 根据正则模式提取代码
     *
     * @param content 原始内容
     * @param pattern 正则模式
     * @return 提取的代码
     */
    public static String extractCodeByPattern(String content, Pattern pattern) {
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * 按给定正则首次匹配整段文本（{@link Matcher#group(int) group(0)}），用于无捕获组或需要完整匹配串的场景。
     *
     * @param content 原始内容
     * @param pattern 正则模式
     * @return 首次匹配的完整子串；未匹配则 {@code null}
     */
    public static String extractFullMatch(String content, Pattern pattern) {
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(0);
        }
        return null;
    }

    /**
     * 从左到右返回第一个非空且 trim 后非空的字符串；全不满足或 {@code values == null} 时返回 {@code null}。
     *
     * @param values 候选字符串（如多种解析策略的结果依次传入）
     * @return 第一个有效字符串，否则 {@code null}
     */
    public static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (v != null && !v.trim().isEmpty()) {
                return v;
            }
        }
        return null;
    }
}

package com.casy.casyaicodemother.util;

import cn.hutool.core.util.StrUtil;

/**
 * 从用户提示词提炼应用短标题，避免直接截取前 12 字变成半句话。
 */
public final class AppNameUtils {

    public static final int DEFAULT_MAX_LEN = 24;

    private static final java.util.regex.Pattern PREFIX = java.util.regex.Pattern.compile(
            "^(请|麻烦|帮我)?(用\\s*[A-Za-z0-9.+#]+\\s*)?(来)?(做|生成|创建|搭建|写|开发)(一个完整的|一个|一份|一套)?",
            java.util.regex.Pattern.CASE_INSENSITIVE);

    private AppNameUtils() {
    }

    public static String derive(String prompt) {
        return derive(prompt, DEFAULT_MAX_LEN);
    }

    public static String derive(String prompt, int maxLen) {
        if (StrUtil.isBlank(prompt)) {
            return "未命名应用";
        }
        String firstLine = prompt.trim().split("\\r?\\n", 2)[0];
        String sentence = firstLine.replaceFirst("[。！？；;].*$", "").trim();
        String stripped = PREFIX.matcher(sentence).replaceFirst("").trim();
        String title = stripped.length() >= 2 ? stripped : sentence;
        int cut = indexOfAny(title, '，', ',', '：', ':');
        if (cut > 4 && cut < maxLen) {
            title = title.substring(0, cut);
        }
        if (title.length() > maxLen) {
            title = title.substring(0, maxLen).replaceAll("[的与和及\\s]+$", "");
        }
        return StrUtil.blankToDefault(title.trim(), "未命名应用");
    }

    private static int indexOfAny(String text, char... chars) {
        int best = -1;
        for (char c : chars) {
            int i = text.indexOf(c);
            if (i >= 0 && (best < 0 || i < best)) {
                best = i;
            }
        }
        return best;
    }
}

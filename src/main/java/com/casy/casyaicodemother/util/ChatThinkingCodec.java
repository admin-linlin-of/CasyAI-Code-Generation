package com.casy.casyaicodemother.util;

import cn.hutool.core.util.StrUtil;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 深度思考内容与 AI 正文在 t_chat_history.message 中共存时的编解码。
 * 格式：{@code <aiThinking>...</aiThinking>} + 正文（与 fileWrite 等工具标签一致，无需改表）。
 */
public final class ChatThinkingCodec {

    private static final Pattern THINKING_BLOCK = Pattern.compile(
            "<aiThinking>([\\s\\S]*?)</aiThinking>\\s*",
            Pattern.CASE_INSENSITIVE);

    private ChatThinkingCodec() {
    }

    public static String composeForSave(String thinking, String body) {
        String normalizedBody = StrUtil.nullToEmpty(body);
        if (StrUtil.isBlank(thinking)) {
            return normalizedBody;
        }
        return "<aiThinking>" + thinking.trim() + "</aiThinking>\n\n" + normalizedBody;
    }

    public static String extractThinking(String message) {
        if (StrUtil.isBlank(message)) {
            return null;
        }
        Matcher matcher = THINKING_BLOCK.matcher(message);
        if (!matcher.find()) {
            return null;
        }
        String thinking = matcher.group(1);
        return StrUtil.isBlank(thinking) ? null : thinking.trim();
    }

    /** 加载 Redis 记忆时去掉 thinking，避免把 reasoning 原文喂回模型 */
    public static String stripThinking(String message) {
        if (StrUtil.isBlank(message)) {
            return message;
        }
        return THINKING_BLOCK.matcher(message).replaceFirst("").trim();
    }
}

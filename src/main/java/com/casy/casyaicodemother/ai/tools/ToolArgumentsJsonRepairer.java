package com.casy.casyaicodemother.ai.tools;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 工具参数 JSON 容错修复器。
 * <p>
 * LLM 返回的 writeFile arguments 常因 content 内含未转义双引号而非法，
 * 导致 langchain4j 在 {@code DefaultToolExecutor.prepareArguments} 阶段 Jackson 解析失败。
 * 本类在 JSON 合法时原样返回；非法时尝试按字段提取并重新序列化为合法 JSON。
 */
final class ToolArgumentsJsonRepairer {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    /** 匹配 well-formed 的 relativeFilePath 字段值 */
    private static final Pattern PATH_PATTERN = Pattern.compile(
            "\"relativeFilePath\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    /** 匹配 append 布尔字段（分块写入时使用） */
    private static final Pattern APPEND_PATTERN = Pattern.compile("\"append\"\\s*:\\s*(true|false)");

    private ToolArgumentsJsonRepairer() {
    }

    /**
     * 尝试修复工具 arguments JSON。
     *
     * @param toolName      工具名，目前仅对 writeFile 做专项修复
     * @param argumentsJson LLM 返回的原始 arguments 字符串
     * @return 修复后的 JSON；无法修复时返回原字符串
     */
    static String repair(String toolName, String argumentsJson) {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return argumentsJson;
        }
        if (canParse(argumentsJson)) {
            return argumentsJson;
        }
        if ("writeFile".equals(toolName)) {
            return repairWriteFileArguments(argumentsJson);
        }
        return argumentsJson;
    }

    private static boolean canParse(String json) {
        try {
            MAPPER.readValue(json, MAP_TYPE);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * 修复 writeFile 的非法 JSON。
     * <p>
     * 典型坏 JSON：{@code {"relativeFilePath":"src/App.vue","content":"<div class="box">..."}}
     * content 内双引号提前截断字符串。修复策略：正则提取 path，content 取 "content":" 之后到末尾 } 前的原始文本，再用 Jackson 重序列化。
     */
    private static String repairWriteFileArguments(String json) {
        String trimmed = json.trim();
        Matcher pathMatcher = PATH_PATTERN.matcher(trimmed);
        if (!pathMatcher.find()) {
            return json;
        }
        String relativeFilePath = unescapeJsonString(pathMatcher.group(1));

        int contentKey = trimmed.indexOf("\"content\"");
        if (contentKey < 0) {
            return json;
        }
        int colon = trimmed.indexOf(':', contentKey);
        if (colon < 0) {
            return json;
        }
        int valueStart = colon + 1;
        while (valueStart < trimmed.length() && Character.isWhitespace(trimmed.charAt(valueStart))) {
            valueStart++;
        }
        if (valueStart >= trimmed.length() || trimmed.charAt(valueStart) != '"') {
            return json;
        }
        // content 值从开引号后一位开始，一直取到 JSON 末尾（去掉闭合 } 及可能的尾引号）
        int contentStart = valueStart + 1;
        int contentEnd = trimmed.length();
        if (trimmed.endsWith("}")) {
            contentEnd = trimmed.length() - 1;
            while (contentEnd > contentStart && Character.isWhitespace(trimmed.charAt(contentEnd - 1))) {
                contentEnd--;
            }
            if (contentEnd > contentStart && trimmed.charAt(contentEnd - 1) == '"') {
                contentEnd--;
            }
        }
        if (contentStart >= contentEnd) {
            return json;
        }
        String content = trimmed.substring(contentStart, contentEnd);

        boolean append = false;
        Matcher appendMatcher = APPEND_PATTERN.matcher(trimmed);
        if (appendMatcher.find()) {
            append = Boolean.parseBoolean(appendMatcher.group(1));
        }

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("relativeFilePath", relativeFilePath);
        map.put("content", content);
        map.put("append", append);
        try {
            return MAPPER.writeValueAsString(map);
        } catch (Exception ignored) {
            return json;
        }
    }

    private static String unescapeJsonString(String value) {
        try {
            return MAPPER.readValue("\"" + value + "\"", String.class);
        } catch (Exception ignored) {
            return value.replace("\\\"", "\"").replace("\\\\", "\\");
        }
    }
}

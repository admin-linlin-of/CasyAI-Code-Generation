package com.casy.casyaicodemother.core.parser;

import com.casy.casyaicodemother.ai.model.MultiFileCodeResult;
import com.casy.casyaicodemother.exception.BusinessException;
import com.casy.casyaicodemother.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.casy.casyaicodemother.constant.CodeParserConstant.*;
import static com.casy.casyaicodemother.util.commUtil.*;

@Slf4j
public class MultiFileCodeParser implements CodeParser{

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /** 缺少 ```css 围栏时，从单文件 HTML 的 <style> 拆出样式 */
    private static final Pattern STYLE_TAG_PATTERN =
            Pattern.compile("(?is)<style[^>]*>([\\s\\S]*?)</style>");

    /** 无 src 的 script，避免把外链当成 script.js */
    private static final Pattern INLINE_SCRIPT_PATTERN =
            Pattern.compile("(?is)<script(?![^>]*\\bsrc\\s*=)[^>]*>([\\s\\S]*?)</script>");

    @Override
    public Object parseCode(String codeContent) {
        MultiFileCodeResult result = new MultiFileCodeResult();

        try {
            JsonNode jsonNode = objectMapper.readTree(codeContent);
            if (jsonNode.has("htmlCode") && jsonNode.has("cssCode") && jsonNode.has("jsCode")) {
                String htmlCode = jsonNode.get("htmlCode").asText();
                String cssCode = jsonNode.get("cssCode").asText();
                String jsCode = jsonNode.get("jsCode").asText();

                if (htmlCode != null && !htmlCode.trim().isEmpty() &&
                    cssCode != null && !cssCode.trim().isEmpty() &&
                    jsCode != null && !jsCode.trim().isEmpty()) {
                    result.setHtmlCode(htmlCode.trim());
                    result.setCssCode(cssCode.trim());
                    result.setJsCode(jsCode.trim());
                    log.info("成功从 JSON 格式解析代码");
                    return result;
                }
            }
        } catch (Exception e) {
            log.debug("JSON 格式解析失败，尝试 Markdown 格式: {}", e.getMessage());
        }

        String htmlCode = firstNonBlank(
                extractCodeByPattern(codeContent, HTML_CODE_PATTERN),
                extractFullMatch(codeContent, LOOSE_HTML_PATTERN));
        String cssCode = firstNonBlank(
                extractCodeByPattern(codeContent, CSS_CODE_PATTERN),
                extractCodeByPattern(codeContent, LOOSE_CSS_PATTERN));
        String jsCode = firstNonBlank(
                extractCodeByPattern(codeContent, JS_CODE_PATTERN),
                extractCodeByPattern(codeContent, JS_FENCE_RELAXED_PATTERN),
                extractCodeByPattern(codeContent, LOOSE_JS_LABEL_PATTERN));
        if (htmlCode != null && !htmlCode.trim().isEmpty()) {
            result.setHtmlCode(htmlCode.trim());
        } else {
            log.info("AI内容Html解析失败：{}", codeContent);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成异常，请稍后重试！");
        }
        // 用户常要求「单文件」，模型把 CSS/JS 写进 HTML；围栏缺失时从标签拆出
        if (cssCode == null || cssCode.trim().isEmpty()) {
            cssCode = extractConcatenated(STYLE_TAG_PATTERN, result.getHtmlCode());
        }
        if (jsCode == null || jsCode.trim().isEmpty()) {
            jsCode = extractConcatenated(INLINE_SCRIPT_PATTERN, result.getHtmlCode());
        }
        if (cssCode != null && !cssCode.trim().isEmpty()) {
            result.setCssCode(cssCode.trim());
        } else {
            log.info("AI内容css解析失败：{}", codeContent);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成异常，请稍后重试！");
        }
        if (jsCode != null && !jsCode.trim().isEmpty()) {
            result.setJsCode(jsCode.trim());
        } else {
            log.info("AI内容js解析失败：{}", codeContent);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成异常，请稍后重试！");
        }
        return result;
    }

    private static String extractConcatenated(Pattern pattern, String html) {
        if (html == null) {
            return null;
        }
        Matcher matcher = pattern.matcher(html);
        StringBuilder collected = new StringBuilder();
        while (matcher.find()) {
            String block = matcher.group(1);
            if (block == null || block.trim().isEmpty()) {
                continue;
            }
            if (collected.length() > 0) {
                collected.append("\n\n");
            }
            collected.append(block.trim());
        }
        return collected.length() == 0 ? null : collected.toString();
    }
}

package com.casy.casyaicodemother.core.parser;

import com.casy.casyaicodemother.ai.model.MultiFileCodeResult;
import com.casy.casyaicodemother.exception.BusinessException;
import com.casy.casyaicodemother.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static com.casy.casyaicodemother.constant.CodeParserConstant.*;
import static com.casy.casyaicodemother.util.commUtil.*;

@Slf4j
public class MultiFileCodeParser implements CodeParser{

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Object parseCode(String codeContent) {
        MultiFileCodeResult result = new MultiFileCodeResult();

        // 首先尝试解析为 JSON 格式（支持 deepseek 等模型的结构化输出）
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
            // JSON 解析失败，继续尝试 Markdown 格式
            log.debug("JSON 格式解析失败，尝试 Markdown 格式: {}", e.getMessage());
        }

        // 回退到原有的 Markdown 代码块解析逻辑
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
        // 设置HTML代码
        if (htmlCode != null && !htmlCode.trim().isEmpty()) {
            result.setHtmlCode(htmlCode.trim());
        } else {
            // 没有html直接报错
            log.info("AI内容Html解析失败：{}", codeContent);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成异常，请稍后重试！");
        }
        // 设置CSS代码
        if (cssCode != null && !cssCode.trim().isEmpty()) {
            result.setCssCode(cssCode.trim());
        } else {
            log.info("AI内容css解析失败：{}", codeContent);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成异常，请稍后重试！");
        }
        // 设置JS代码
        if (jsCode != null && !jsCode.trim().isEmpty()) {
            result.setJsCode(jsCode.trim());
        } else {
            log.info("AI内容js解析失败：{}", codeContent);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成异常，请稍后重试！");
        }
        return result;
    }
}

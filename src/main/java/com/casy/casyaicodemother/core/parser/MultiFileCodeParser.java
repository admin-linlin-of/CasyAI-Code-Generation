package com.casy.casyaicodemother.core.parser;

import com.casy.casyaicodemother.ai.model.MultiFileCodeResult;
import com.casy.casyaicodemother.exception.BusinessException;
import com.casy.casyaicodemother.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;

import static com.casy.casyaicodemother.constant.CodeParserConstant.*;
import static com.casy.casyaicodemother.util.commUtil.*;

@Slf4j
public class MultiFileCodeParser implements CodeParser{

    @Override
    public Object parseCode(String codeContent) {
        MultiFileCodeResult result = new MultiFileCodeResult();
        // 提取各类代码
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

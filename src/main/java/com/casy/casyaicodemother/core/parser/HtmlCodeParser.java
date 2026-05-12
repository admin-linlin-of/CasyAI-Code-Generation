package com.casy.casyaicodemother.core.parser;

import com.casy.casyaicodemother.ai.model.HtmlCodeResult;
import com.casy.casyaicodemother.exception.BusinessException;
import com.casy.casyaicodemother.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;

import static com.casy.casyaicodemother.constant.CodeParserConstant.HTML_CODE_PATTERN;
import static com.casy.casyaicodemother.constant.CodeParserConstant.LOOSE_HTML_PATTERN;
import static com.casy.casyaicodemother.util.commUtil.*;

@Slf4j
public class HtmlCodeParser implements CodeParser{

    @Override
    public Object parseCode(String codeContent) {
        HtmlCodeResult result = new HtmlCodeResult();
        // 提取 HTML 代码
        String htmlCode = firstNonBlank(
                extractCodeByPattern(codeContent, HTML_CODE_PATTERN),
                extractFullMatch(codeContent, LOOSE_HTML_PATTERN));
        if (htmlCode != null && !htmlCode.trim().isEmpty()) {
            result.setHtmlCode(htmlCode.trim());
        } else {
            // 没有html直接报错
            log.info("AI内容解析失败：{}", codeContent);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成异常，请稍后重试！");
        }
        return result;
    }
}

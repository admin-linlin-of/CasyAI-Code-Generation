package com.casy.casyaicodemother.core.parser;

import com.casy.casyaicodemother.exception.BusinessException;
import com.casy.casyaicodemother.exception.ErrorCode;
import com.casy.casyaicodemother.model.enums.CodeGenTypeEnum;

/**
 * 代码解析执行器
 * 根据代码生成类型执行相应的解析逻辑
 *
 * <h3>方法流程示意图：</h3>
 * <img src="../../../../../../javadoc/doc-files/内容解析策略模型.png" alt="登录验证流程" width="700"  height="500"/>
 */
public class CodeParserExecutor {

    private static final MultiFileCodeParser multiFileCodeParser = new MultiFileCodeParser();

    private static final HtmlCodeParser htmlCodeParser = new HtmlCodeParser();

    /**
     * 执行代码解析
     *
     * @param codeGenTypeEnum 代码生成类型
     * @param context 代码内容
     * @return 解析结果（HtmlCodeResult 或 MultiFileCodeResult）
     */
    public static Object executeParser(CodeGenTypeEnum codeGenTypeEnum, String context) {
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成类型为空");
        }
        return switch (codeGenTypeEnum) {
            case HTML -> htmlCodeParser.parseCode(context);
            case MULTI_FILE -> multiFileCodeParser.parseCode(context);
            default -> {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的生成类型：" + codeGenTypeEnum.getValue());
            }
        };
    }
}

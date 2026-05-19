package com.casy.casyaicodemother.core.chatModel;

import com.casy.casyaicodemother.ai.AiCodeGeneratorService;
import com.casy.casyaicodemother.exception.BusinessException;
import com.casy.casyaicodemother.exception.ErrorCode;
import com.casy.casyaicodemother.model.enums.ModelTypeEnum;

/**
 * 模型选择执行器
 * 根据模型类型执行相应的模型服务
 *
 */
public class ChatModelExecutor {

    private static final DeepSeekChatModel deepSeekChatModel = new DeepSeekChatModel();
    private static final GptChatModel gptChatModel = new GptChatModel();

    /**
     * 执行代码解析
     *
     * @param modelType 模型类型
     * @return 解析结果（HtmlCodeResult 或 MultiFileCodeResult）
     */
    public static AiCodeGeneratorService executeParser(ModelTypeEnum modelType) {
        if (modelType == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "模型类型为空");
        }
        return switch (modelType) {
            case GPT -> gptChatModel.getAiService();
            case DEEPSEEK -> deepSeekChatModel.getAiService();
            default -> {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的模型类型：" + modelType.getModelName());
            }
        };
    }
}

package com.casy.casyaicodemother.core.chatModel;

import com.casy.casyaicodemother.ai.AiCodeGeneratorService;
import com.casy.casyaicodemother.exception.BusinessException;
import com.casy.casyaicodemother.exception.ErrorCode;
import com.casy.casyaicodemother.exception.ThrowUtils;
import com.casy.casyaicodemother.model.enums.ModelTypeEnum;
import org.springframework.stereotype.Component;

@Component
public class ChatModelExecutor {

    private static final DeepSeekChatModel deepSeekChatModel = new DeepSeekChatModel();
    private static final GptChatModel gptChatModel = new GptChatModel();

    /**
     * 执行代码解析
     *
     * @param modelType 模型类型
     * @param appId 应用id
     * @return 解析结果（HtmlCodeResult 或 MultiFileCodeResult）
     */
    public static AiCodeGeneratorService executeParser(ModelTypeEnum modelType, Long appId) {
        if (modelType == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "模型类型为空");
        }
        ThrowUtils.throwIf(appId == null, ErrorCode.SYSTEM_ERROR, "应用ID不能为空");
        return switch (modelType) {
            case GPT -> gptChatModel.getAiService(appId);
            case DEEPSEEK -> deepSeekChatModel.getAiService(appId);
            default -> {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的模型类型：" + modelType.getModelName());
            }
        };
    }
}

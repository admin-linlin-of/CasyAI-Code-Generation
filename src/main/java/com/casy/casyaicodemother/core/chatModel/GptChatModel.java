package com.casy.casyaicodemother.core.chatModel;

import com.casy.casyaicodemother.ai.AiCodeGeneratorService;
import com.casy.casyaicodemother.ai.AiCodeGeneratorServiceFactory;
import com.casy.casyaicodemother.constant.Global;

public class GptChatModel implements ChatModel {
    @Override
    public AiCodeGeneratorService getAiService(Long appId) {
        return Global.getSpringContext().getBean(AiCodeGeneratorServiceFactory.class).getGptCodeGeneratorService(appId);
    }
}

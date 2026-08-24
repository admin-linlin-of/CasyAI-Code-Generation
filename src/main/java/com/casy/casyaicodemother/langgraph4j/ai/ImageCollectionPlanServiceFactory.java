package com.casy.casyaicodemother.langgraph4j.ai;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration
public class ImageCollectionPlanServiceFactory {

    /**
     * prototype：节点每次 getBean 都新建 AiServices + 新的 ChatModel。
     * ChatModel 必须走方法参数注入，不能 @Resource 到这个单例配置类上，否则模型仍是启动时那个单例。
     */
    @Bean
    @Scope("prototype")
    public ImageCollectionPlanService createImageCollectionPlanService(
            @Qualifier("deepSeekV4FlashChatModel") ChatModel chatModel) {
        return AiServices.builder(ImageCollectionPlanService.class)
                .chatModel(chatModel)
                .build();
    }
}

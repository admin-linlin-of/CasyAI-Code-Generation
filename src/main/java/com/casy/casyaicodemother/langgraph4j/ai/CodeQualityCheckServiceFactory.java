package com.casy.casyaicodemother.langgraph4j.ai;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Slf4j
@Configuration
public class CodeQualityCheckServiceFactory {

    @Bean
    @Scope("prototype")
    public CodeQualityCheckService createCodeQualityCheckService(
            @Qualifier("deepSeekV4FlashChatModel") ChatModel chatModel) {
        return AiServices.builder(CodeQualityCheckService.class)
                .chatModel(chatModel)
                .build();
    }
}

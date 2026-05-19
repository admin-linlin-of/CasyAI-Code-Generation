package com.casy.casyaicodemother.ai;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiCodeGeneratorServiceFactory {

    @Bean
    public AiCodeGeneratorService aiCodeGeneratorService(
            @Qualifier("openAiChatModel") ChatModel chatModel,
            @Qualifier("openAiStreamingChatModel") StreamingChatModel streamingChatModel) {
        return AiServices.builder(AiCodeGeneratorService.class)
                .chatModel(chatModel)
                .streamingChatModel(streamingChatModel)
                .build();
    }

    @Bean
    public AiCodeGeneratorService gptAiCodeGeneratorService(
            @Qualifier("gptChatModel") ChatModel chatModel,
            @Qualifier("gptStreamingChatModel") StreamingChatModel streamingChatModel) {
        return AiServices.builder(AiCodeGeneratorService.class)
                .chatModel(chatModel)
                .streamingChatModel(streamingChatModel)
                .build();
    }
}

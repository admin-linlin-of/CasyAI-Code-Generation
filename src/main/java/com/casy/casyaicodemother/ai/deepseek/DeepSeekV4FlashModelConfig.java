package com.casy.casyaicodemother.ai.deepseek;

import com.casy.casyaicodemother.ai.DefaultModelProvider;
import com.casy.casyaicodemother.ai.ModelProvider;
import com.casy.casyaicodemother.model.enums.ModelTypeEnum;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DeepSeekV4FlashModelConfig {

    @Bean
    ModelProvider deepSeekV4FlashModelProvider(
            @Qualifier("openAiChatModel") ChatModel chatModel,
            @Qualifier("openAiStreamingChatModel") StreamingChatModel streamingChatModel) {
        return new DefaultModelProvider(ModelTypeEnum.DEEPSEEKFLASH, chatModel, streamingChatModel);
    }
}

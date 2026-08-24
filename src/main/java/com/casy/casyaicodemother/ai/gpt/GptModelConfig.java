package com.casy.casyaicodemother.ai.gpt;

import com.casy.casyaicodemother.ai.DefaultModelProvider;
import com.casy.casyaicodemother.ai.ModelProvider;
import com.casy.casyaicodemother.config.LangChain4jHttpClientFactory;
import com.casy.casyaicodemother.model.enums.ModelTypeEnum;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration
@EnableConfigurationProperties(GptModelProperties.class)
public class GptModelConfig {

    @Bean("gptChatModel")
    @Scope("prototype")
    ChatModel gptChatModel(GptModelProperties g, LangChain4jHttpClientFactory httpClientFactory) {
        return OpenAiChatModel.builder()
                .httpClientBuilder(httpClientFactory.jdkHttpClientBuilder())
                .timeout(httpClientFactory.requestTimeout())
                .baseUrl(g.getBaseUrl())
                .apiKey(g.getApiKey())
                .modelName(g.getModelName())
                .maxTokens(g.getMaxTokens())
                .logRequests(g.getLogRequests())
                .logResponses(g.getLogResponses())
                .strictJsonSchema(g.getStrictJsonSchema())
                .responseFormat(g.getResponseFormat())
                .build();
    }

    @Bean("gptStreamingChatModel")
    @Scope("prototype")
    StreamingChatModel gptStreamingChatModel(GptModelProperties g, LangChain4jHttpClientFactory httpClientFactory) {
        return OpenAiStreamingChatModel.builder()
                .httpClientBuilder(httpClientFactory.jdkHttpClientBuilder())
                .timeout(httpClientFactory.requestTimeout())
                .baseUrl(g.getBaseUrl())
                .apiKey(g.getApiKey())
                .modelName(g.getModelName())
                .maxTokens(g.getMaxTokens())
                .logRequests(g.getLogRequests())
                .logResponses(g.getLogResponses())
                .strictJsonSchema(g.getStrictJsonSchema())
                .responseFormat(g.getResponseFormat())
                .build();
    }

    /** 注册 GPT 模型策略，参见 {@link com.casy.casyaicodemother.ai.deepseek.DeepSeekV4FlashModelConfig} */
    @Bean
    ModelProvider gptModelProvider(
            @Qualifier("gptChatModel") ObjectProvider<ChatModel> chatModel,
            @Qualifier("gptStreamingChatModel") ObjectProvider<StreamingChatModel> streamingChatModel) {
        return new DefaultModelProvider(ModelTypeEnum.GPT, chatModel, streamingChatModel);
    }
}

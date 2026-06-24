package com.casy.casyaicodemother.ai.deepseek;

import com.casy.casyaicodemother.ai.DefaultModelProvider;
import com.casy.casyaicodemother.ai.ModelProvider;
import com.casy.casyaicodemother.config.LangChain4jHttpClientFactory;
import com.casy.casyaicodemother.model.enums.ModelTypeEnum;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DeepSeekV4ProModelProperties.class)
public class DeepSeekV4ProModelConfig {

    @Bean("deepSeekV4ProChatModel")
    ChatModel deepSeekV4ProChatModel(DeepSeekV4ProModelProperties g, LangChain4jHttpClientFactory httpClientFactory) {
        return OpenAiChatModel.builder()
                .httpClientBuilder(httpClientFactory.jdkHttpClientBuilder())
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

    @Bean("deepSeekV4ProStreamingChatModel")
    StreamingChatModel deepSeekV4ProStreamingChatModel(DeepSeekV4ProModelProperties g, LangChain4jHttpClientFactory httpClientFactory) {
        return OpenAiStreamingChatModel.builder()
                .httpClientBuilder(httpClientFactory.jdkHttpClientBuilder())
                .baseUrl(g.getBaseUrl())
                .apiKey(g.getApiKey())
                .modelName(g.getModelName())
                .maxTokens(g.getMaxTokens())
                .logRequests(g.getLogRequests())
                .logResponses(g.getLogResponses())
                .strictJsonSchema(g.getStrictJsonSchema())
                .responseFormat(g.getResponseFormat())
                // 解析 API 返回的 reasoning_content，触发 onPartialThinking 回调
                .returnThinking(true)
                .build();
    }

    /** 注册 DeepSeek V4 Pro 模型策略 */
    @Bean
    ModelProvider deepSeekV4ProModelProvider(
            @Qualifier("deepSeekV4ProChatModel") ChatModel chatModel,
            @Qualifier("deepSeekV4ProStreamingChatModel") StreamingChatModel streamingChatModel) {
        return new DefaultModelProvider(ModelTypeEnum.DEEPSEEKPRO, chatModel, streamingChatModel);
    }
}

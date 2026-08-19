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

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableConfigurationProperties(DeepSeekV4ProModelProperties.class)
public class DeepSeekV4ProModelConfig {

    @Bean("deepSeekV4ProChatModel")
    ChatModel deepSeekV4ProChatModel(DeepSeekV4ProModelProperties g, LangChain4jHttpClientFactory httpClientFactory) {
        boolean thinking = !Boolean.FALSE.equals(g.getThinkingEnabled());
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
                .returnThinking(thinking)
                .customParameters(thinkingParams(thinking))
                .build();
    }

    @Bean("deepSeekV4ProStreamingChatModel")
    StreamingChatModel deepSeekV4ProStreamingChatModel(DeepSeekV4ProModelProperties g, LangChain4jHttpClientFactory httpClientFactory) {
        boolean thinking = !Boolean.FALSE.equals(g.getThinkingEnabled());
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
                .returnThinking(thinking)
                .customParameters(thinkingParams(thinking))
                .build();
    }

    private static Map<String, Object> thinkingParams(boolean enabled) {
        Map<String, Object> params = new HashMap<>();
        params.put("thinking", Map.of("type", enabled ? "enabled" : "disabled"));
        return params;
    }

    /** 注册 DeepSeek V4 Pro 模型策略 */
    @Bean
    ModelProvider deepSeekV4ProModelProvider(
            @Qualifier("deepSeekV4ProChatModel") ChatModel chatModel,
            @Qualifier("deepSeekV4ProStreamingChatModel") StreamingChatModel streamingChatModel) {
        return new DefaultModelProvider(ModelTypeEnum.DEEPSEEKPRO, chatModel, streamingChatModel);
    }
}

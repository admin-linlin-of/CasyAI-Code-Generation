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
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * DeepSeek V4 Flash（代码生成用）。
 * thinking / maxTokens 均由配置控制；开 thinking 时建议同步加大 max-tokens。
 */
@Configuration
@EnableConfigurationProperties({
        DeepSeekFlashChatModelProperties.class,
        DeepSeekFlashStreamingChatModelProperties.class
})
public class DeepSeekV4FlashModelConfig {

    @Bean("deepSeekV4FlashChatModel")
    ChatModel deepSeekV4FlashChatModel(DeepSeekFlashChatModelProperties g, LangChain4jHttpClientFactory httpClientFactory) {
        boolean thinking = Boolean.TRUE.equals(g.getThinkingEnabled());
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

    @Bean("deepSeekV4FlashStreamingChatModel")
    StreamingChatModel deepSeekV4FlashStreamingChatModel(
            DeepSeekFlashStreamingChatModelProperties s,
            DeepSeekFlashChatModelProperties chat,
            LangChain4jHttpClientFactory httpClientFactory) {
        String baseUrl = StringUtils.hasText(s.getBaseUrl()) ? s.getBaseUrl() : chat.getBaseUrl();
        String apiKey = StringUtils.hasText(s.getApiKey()) ? s.getApiKey() : chat.getApiKey();
        String modelName = StringUtils.hasText(s.getModelName()) ? s.getModelName() : chat.getModelName();
        Integer maxTokens = s.getMaxTokens() != null ? s.getMaxTokens() : chat.getMaxTokens();
        Boolean logRequests = s.getLogRequests() != null ? s.getLogRequests() : chat.getLogRequests();
        Boolean logResponses = s.getLogResponses() != null ? s.getLogResponses() : chat.getLogResponses();
        Boolean strictJsonSchema = s.getStrictJsonSchema() != null ? s.getStrictJsonSchema() : chat.getStrictJsonSchema();
        String responseFormat = StringUtils.hasText(s.getResponseFormat()) ? s.getResponseFormat() : chat.getResponseFormat();
        boolean thinking = s.getThinkingEnabled() != null
                ? Boolean.TRUE.equals(s.getThinkingEnabled())
                : Boolean.TRUE.equals(chat.getThinkingEnabled());
        return OpenAiStreamingChatModel.builder()
                .httpClientBuilder(httpClientFactory.jdkHttpClientBuilder())
                .timeout(httpClientFactory.requestTimeout())
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .maxTokens(maxTokens)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .strictJsonSchema(strictJsonSchema)
                .responseFormat(responseFormat)
                .returnThinking(thinking)
                .customParameters(thinkingParams(thinking))
                .build();
    }

    @Bean
    ModelProvider deepSeekV4FlashModelProvider(
            @Qualifier("deepSeekV4FlashChatModel") ChatModel chatModel,
            @Qualifier("deepSeekV4FlashStreamingChatModel") StreamingChatModel streamingChatModel) {
        return new DefaultModelProvider(ModelTypeEnum.DEEPSEEKFLASH, chatModel, streamingChatModel);
    }

    private static Map<String, Object> thinkingParams(boolean enabled) {
        Map<String, Object> params = new HashMap<>();
        params.put("thinking", Map.of("type", enabled ? "enabled" : "disabled"));
        return params;
    }
}

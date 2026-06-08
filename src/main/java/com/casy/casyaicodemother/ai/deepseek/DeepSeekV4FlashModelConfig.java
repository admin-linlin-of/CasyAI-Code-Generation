package com.casy.casyaicodemother.ai.deepseek;

import com.casy.casyaicodemother.ai.DefaultModelProvider;
import com.casy.casyaicodemother.ai.ModelProvider;
import com.casy.casyaicodemother.model.enums.ModelTypeEnum;
import dev.langchain4j.http.client.jdk.JdkHttpClient;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * DeepSeek V4 Flash 模型配置（系统默认模型）。
 * <p>
 * 除创建 LangChain4j 的 ChatModel Bean 外，还需注册 {@link ModelProvider} 策略 Bean，
 * 这样 {@link com.casy.casyaicodemother.ai.AiCodeGeneratorServiceFactory} 才能自动发现并使用该模型。
 * <p>
 * 其他模型（GPT、Claude、DeepSeek Pro）的配置类结构与此相同，均遵循「Config + Properties + ModelProvider Bean」三件套。
 */
@Configuration
@EnableConfigurationProperties(DeepSeekV4FlashModelProperties.class)
public class DeepSeekV4FlashModelConfig {

    @Bean("deepSeekV4FlashChatModel")
    ChatModel deepSeekV4FlashChatModel(DeepSeekV4FlashModelProperties g) {
        return OpenAiChatModel.builder()
                .httpClientBuilder(JdkHttpClient.builder())
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

    @Bean("deepSeekV4FlashStreamingChatModel")
    StreamingChatModel deepSeekV4FlashStreamingChatModel(DeepSeekV4FlashModelProperties g) {
        return OpenAiStreamingChatModel.builder()
                .httpClientBuilder(JdkHttpClient.builder())
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

    /**
     * 将 Flash 模型注册为策略，供 Factory 自动收集。
     * 新增其他模型时，复制此 Bean 的写法即可，无需改 Factory。
     */
    @Bean
    ModelProvider deepSeekV4FlashModelProvider(
            @Qualifier("deepSeekV4FlashChatModel") ChatModel chatModel,
            @Qualifier("deepSeekV4FlashStreamingChatModel") StreamingChatModel streamingChatModel) {
        return new DefaultModelProvider(ModelTypeEnum.DEEPSEEKFLASH, chatModel, streamingChatModel);
    }
}

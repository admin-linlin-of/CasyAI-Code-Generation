package com.casy.casyaicodemother.ai.claude;

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

@Configuration
@EnableConfigurationProperties(ClaudeSonnet46Properties.class)
public class ClaudeSonnet46ModelConfig {

    @Bean("claudeSonnet46ChatModel")
    ChatModel ClaudeSonnet46ChatModel(ClaudeSonnet46Properties g) {
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

    @Bean("claudeSonnet46StreamingChatModel")
    StreamingChatModel ClaudeSonnet46StreamingChatModel(ClaudeSonnet46Properties g) {
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

    /** 注册 Claude Sonnet 4.6 模型策略 */
    @Bean
    ModelProvider claudeSonnet46ModelProvider(
            @Qualifier("claudeSonnet46ChatModel") ChatModel chatModel,
            @Qualifier("claudeSonnet46StreamingChatModel") StreamingChatModel streamingChatModel) {
        return new DefaultModelProvider(ModelTypeEnum.CLAUDESONNET, chatModel, streamingChatModel);
    }
}

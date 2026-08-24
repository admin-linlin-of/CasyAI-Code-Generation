package com.casy.casyaicodemother.ai.claude;

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
@EnableConfigurationProperties(ClaudeSonnet46Properties.class)
public class ClaudeSonnet46ModelConfig {

    @Bean("claudeSonnet46ChatModel")
    @Scope("prototype")
    ChatModel ClaudeSonnet46ChatModel(ClaudeSonnet46Properties g, LangChain4jHttpClientFactory httpClientFactory) {
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

    @Bean("claudeSonnet46StreamingChatModel")
    @Scope("prototype")
    StreamingChatModel ClaudeSonnet46StreamingChatModel(ClaudeSonnet46Properties g, LangChain4jHttpClientFactory httpClientFactory) {
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

    @Bean
    ModelProvider claudeSonnet46ModelProvider(
            @Qualifier("claudeSonnet46ChatModel") ObjectProvider<ChatModel> chatModel,
            @Qualifier("claudeSonnet46StreamingChatModel") ObjectProvider<StreamingChatModel> streamingChatModel) {
        return new DefaultModelProvider(ModelTypeEnum.CLAUDESONNET, chatModel, streamingChatModel);
    }
}

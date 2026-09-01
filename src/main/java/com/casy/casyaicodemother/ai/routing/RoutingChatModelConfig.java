package com.casy.casyaicodemother.ai.routing;

import com.casy.casyaicodemother.config.LangChain4jHttpClientFactory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

/**
 * 路由 ChatModel（如 qwen3.5-flash）。
 * 只提供同步 ChatModel，不注册 {@link com.casy.casyaicodemother.ai.ModelProvider}，避免被代码生成策略表选中。
 */
@Configuration
@EnableConfigurationProperties(RoutingChatModelProperties.class)
public class RoutingChatModelConfig {

    /** 每次调用新建实例。OpenAiChatModel.chat() 内部 HTTP execute 是阻塞的，单例会把并发请求排成串行。 */
    @Bean("routingChatModel")
    @Scope("prototype")
    ChatModel routingChatModel(RoutingChatModelProperties g, LangChain4jHttpClientFactory httpClientFactory) {
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
}

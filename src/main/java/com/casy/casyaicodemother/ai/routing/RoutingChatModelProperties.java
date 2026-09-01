package com.casy.casyaicodemother.ai.routing;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 路由专用便宜模型配置，对应 yml {@code langchain4j.open-ai.routing-chat-model}。
 * 仅用于生成类型选择、模型选择，不参与实际代码生成。
 */
@ConfigurationProperties(prefix = "langchain4j.open-ai.routing-chat-model")
@Data
public class RoutingChatModelProperties {
    private String baseUrl;
    private String apiKey;
    private String modelName;
    private Integer maxTokens;
    private Boolean logRequests;
    private Boolean logResponses;
    private Boolean strictJsonSchema;
    private String responseFormat;
}

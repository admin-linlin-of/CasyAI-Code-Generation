package com.casy.casyaicodemother.ai.deepseek;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "langchain4j.open-ai.streaming-chat-model")
public class DeepSeekFlashStreamingChatModelProperties {
    private String baseUrl;
    private String apiKey;
    private String modelName;
    private Integer maxTokens;
    private Boolean logRequests;
    private Boolean logResponses;
    private Boolean strictJsonSchema;
    private String responseFormat;
    /** 为空则回退 chat-model.thinking-enabled */
    private Boolean thinkingEnabled;
}

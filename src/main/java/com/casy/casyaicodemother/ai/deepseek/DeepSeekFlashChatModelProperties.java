package com.casy.casyaicodemother.ai.deepseek;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "langchain4j.open-ai.chat-model")
public class DeepSeekFlashChatModelProperties {
    private String baseUrl;
    private String apiKey;
    private String modelName;
    private Integer maxTokens;
    private Boolean logRequests;
    private Boolean logResponses;
    private Boolean strictJsonSchema;
    private String responseFormat;
    /** 是否开启 DeepSeek 思考；默认 false，避免 reasoning 占满 token 导致 content 为空 */
    private Boolean thinkingEnabled = false;
}

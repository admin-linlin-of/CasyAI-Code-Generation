package com.casy.casyaicodemother.ai.deepseek;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai.deepseek")
@Data
public class DeepSeekV4ProModelProperties {
    private String baseUrl;
    private String apiKey;
    private String modelName;
    private Integer maxTokens;
    private Boolean logRequests;
    private Boolean logResponses;
    private Boolean strictJsonSchema;
    private String responseFormat;
}


package com.casy.casyaicodemother.ai.deepseek;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * DeepSeek V4 Flash 模型配置属性（系统默认模型）。
 * <p>
 * 对应 application.yaml 中 {@code ai.deepseek-flash.*} 配置项。
 */
@ConfigurationProperties(prefix = "ai.deepseek-flash")
@Data
public class DeepSeekV4FlashModelProperties {
    private String baseUrl;
    private String apiKey;
    private String modelName;
    private Integer maxTokens;
    private Boolean logRequests;
    private Boolean logResponses;
    private Boolean strictJsonSchema;
    private String responseFormat;
}

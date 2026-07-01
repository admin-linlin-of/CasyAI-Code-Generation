package com.casy.casyaicodemother.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "langchain4j.http-client")
public class LangChain4jHttpClientProperties {

    /** 与模型 API 建立连接的超时时间 */
    private Duration connectTimeout = Duration.ofSeconds(30);

    /**
     * 流式 SSE 读超时。Starter 默认 Spring RestClient 约 60s，
     * 长生成会被 {@code JdkClientHttpRequest$TimeoutHandler} 掐断并报 {@code closed}。
     */
    private Duration readTimeout = Duration.ofMinutes(10);
}

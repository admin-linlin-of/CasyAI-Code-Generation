package com.casy.casyaicodemother.config;

import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.jdk.JdkHttpClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LangChain4jHttpClientFactory {

    private final LangChain4jHttpClientProperties properties;

    /** 供各 OpenAi 兼容模型与 Starter 覆盖 Bean 共用，统一 connect/read 超时 */
    public HttpClientBuilder jdkHttpClientBuilder() {
        return JdkHttpClient.builder()
                .connectTimeout(properties.getConnectTimeout())
                .readTimeout(properties.getReadTimeout());
    }
}

package com.casy.casyaicodemother.config;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.jdk.JdkHttpClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class LangChain4jHttpClientFactory {

    private final LangChain4jHttpClientProperties properties;

    public HttpClientBuilder jdkHttpClientBuilder() {
        return new PinnedTimeoutHttpClientBuilder(properties);
    }

    public Duration requestTimeout() {
        return properties.getReadTimeout();
    }

    static final class PinnedTimeoutHttpClientBuilder implements HttpClientBuilder {

        private final LangChain4jHttpClientProperties properties;

        PinnedTimeoutHttpClientBuilder(LangChain4jHttpClientProperties properties) {
            this.properties = properties;
        }

        @Override
        public Duration connectTimeout() {
            return properties.getConnectTimeout();
        }

        @Override
        public HttpClientBuilder connectTimeout(Duration duration) {
            return this;
        }

        @Override
        public Duration readTimeout() {
            return properties.getReadTimeout();
        }

        @Override
        public HttpClientBuilder readTimeout(Duration duration) {
            return this;
        }

        @Override
        public HttpClient build() {
            return JdkHttpClient.builder()
                    .connectTimeout(properties.getConnectTimeout())
                    .readTimeout(properties.getReadTimeout())
                    .build();
        }
    }
}

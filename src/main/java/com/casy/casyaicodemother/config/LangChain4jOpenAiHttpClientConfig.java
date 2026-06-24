package com.casy.casyaicodemother.config;

import dev.langchain4j.http.client.HttpClientBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LangChain4j OpenAI Starter 默认使用 {@code SpringRestClient}，内部通过 Spring 7 的
 * {@code RestClient} 以 {@code String.class} 读取响应体。当响应头为 {@code application/json}
 * 时，消息转换器会优先走 Jackson，将 JSON **对象**反序列化成 {@code String} 会失败
 * （成功时的 chat.completion JSON 与错误时的 {@code {"error":...}} 一样是对象而非 JSON 字符串字面量），
 * 从而抛出 {@code RestClientException: Error while extracting response for type [java.lang.String]}。
 *
 * <p>解决思路：为 {@link dev.langchain4j.model.openai.OpenAiChatModel} 提供 JDK 自带的
 * {@link dev.langchain4j.http.client.jdk.JdkHttpClient}，按原始字节流读取 UTF-8 文本，不参与上述 Jackson 映射。
 * Bean 名称需与 Starter 中 {@code openAiChatModelHttpClientBuilder}、
 * {@code openAiStreamingChatModelHttpClientBuilder} 一致，以覆盖默认 SpringRestClient Bean。
 */
@Configuration
@EnableConfigurationProperties(LangChain4jHttpClientProperties.class)
public class LangChain4jOpenAiHttpClientConfig {

    /** 非流式 chat-model（如 deepseek-v4-flash 同步调用） */
    @Bean("openAiChatModelHttpClientBuilder")
    HttpClientBuilder openAiChatModelHttpClientBuilder(LangChain4jHttpClientFactory httpClientFactory) {
        return httpClientFactory.jdkHttpClientBuilder();
    }

    /** 流式 streaming-chat-model；未覆盖时走 SpringRestClient，默认 60s 读超时 */
    @Bean("openAiStreamingChatModelHttpClientBuilder")
    HttpClientBuilder openAiStreamingChatModelHttpClientBuilder(LangChain4jHttpClientFactory httpClientFactory) {
        return httpClientFactory.jdkHttpClientBuilder();
    }
}

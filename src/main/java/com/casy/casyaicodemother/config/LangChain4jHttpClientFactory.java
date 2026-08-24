package com.casy.casyaicodemother.config;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.jdk.JdkHttpClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

/**
 * LangChain4j 的 HTTP 客户端工厂：统一改用 JDK {@link JdkHttpClient}，并钉死超时、共用一个 client。
 *
 * <h2>为什么不用 Starter 默认的 Spring RestClient</h2>
 * {@code langchain4j-open-ai-spring-boot4-starter} 默认走 {@code SpringRestClient}。
 * 它用 Spring {@code RestClient} 把响应体按 {@code String.class} 抽取；响应头是
 * {@code application/json} 时 Jackson 会把 JSON <b>对象</b>反序列化成 String，直接失败
 * （报 {@code Error while extracting response for type [java.lang.String]}）。
 * 流式场景下 RestClient 的默认读超时大约 60s，长代码生成会被掐断，SSE 报 {@code closed}。
 * <p>
 * JDK HttpClient 按字节流读 UTF-8 文本，不经过 Jackson 消息转换器，超时由本工厂从
 * {@link LangChain4jHttpClientProperties}（配置前缀 {@code langchain4j.http-client}）读取。
 *
 * <h2>谁在用</h2>
 * <ul>
 *   <li>各 {@code *ModelConfig}：{@code OpenAiChatModel}/{@code OpenAiStreamingChatModel}
 *       的 {@code .httpClientBuilder(jdkHttpClientBuilder())} 和 {@code .timeout(requestTimeout())}</li>
 *   <li>{@link LangChain4jOpenAiHttpClientConfig}：用同名 Bean 覆盖 Starter 自动配置的 RestClient Builder</li>
 * </ul>
 *
 * <h2>为什么要 PinnedTimeoutHttpClientBuilder</h2>
 * LangChain4j 建模型时会再调 {@code connectTimeout}/{@code readTimeout} 写入它自己的默认值，
 * 可能把我们在 yml 里配的 10 分钟读超时改回去。内部 Builder 对 setter 直接忽略，只认 properties。
 *
 * <h2>为什么只建一个 HttpClient</h2>
 * ChatModel 是 prototype，每次 {@code build()} 都会调到这里。JDK HttpClient 本身线程安全，
 * 共用一个实例即可并发打多路 SSE，不必每个模型再 new 一套连接池。
 */
@Component
@RequiredArgsConstructor
public class LangChain4jHttpClientFactory {

    private final LangChain4jHttpClientProperties properties;

    /**
     * 进程内唯一的 JDK HttpClient。{@link PinnedTimeoutHttpClientBuilder#build()} 用 CAS 写入，
     * 并发第一次 build 也不会建出两个 client。
     */
    private final AtomicReference<HttpClient> sharedClient = new AtomicReference<>();

    /**
     * 给 OpenAi*Model 和 Starter 覆盖 Bean 用的 Builder。
     * 每次返回新 Builder 对象（LangChain4j 会持有它），但 {@link #build()} 始终拿到同一个 HttpClient。
     */
    public HttpClientBuilder jdkHttpClientBuilder() {
        return new PinnedTimeoutHttpClientBuilder(properties, sharedClient);
    }

    /**
     * 模型 {@code .timeout(...)} 用的总超时，与 HTTP 读超时对齐，避免模型层先于 HTTP 层超时。
     */
    public Duration requestTimeout() {
        return properties.getReadTimeout();
    }

    /**
     * 只认 {@link LangChain4jHttpClientProperties} 的超时，忽略 LangChain4j 后续写入的 timeout。
     */
    static final class PinnedTimeoutHttpClientBuilder implements HttpClientBuilder {

        private final LangChain4jHttpClientProperties properties;
        private final AtomicReference<HttpClient> sharedClient;

        PinnedTimeoutHttpClientBuilder(LangChain4jHttpClientProperties properties,
                                       AtomicReference<HttpClient> sharedClient) {
            this.properties = properties;
            this.sharedClient = sharedClient;
        }

        /** 建连超时：yml {@code langchain4j.http-client.connect-timeout}，默认 30s */
        @Override
        public Duration connectTimeout() {
            return properties.getConnectTimeout();
        }

        /**
         * 有意空实现。LangChain4j 构建模型时会把默认超时 set 进来，若这里真的改掉，
         * yml 里配的长读超时就失效了。
         */
        @Override
        public HttpClientBuilder connectTimeout(Duration duration) {
            return this;
        }

        /** SSE/响应体读超时：yml {@code langchain4j.http-client.read-timeout}，默认 10 分钟 */
        @Override
        public Duration readTimeout() {
            return properties.getReadTimeout();
        }

        /** 同 {@link #connectTimeout(Duration)}，拒绝被框架覆盖 */
        @Override
        public HttpClientBuilder readTimeout(Duration duration) {
            return this;
        }

        /**
         * 懒创建并复用 JDK HttpClient。
         * CAS：两个线程同时第一次 build 时，只有一个写入成功，另一个丢弃自己 new 出来的那个。
         */
        @Override
        public HttpClient build() {
            HttpClient existing = sharedClient.get();
            if (existing != null) {
                return existing;
            }
            HttpClient created = JdkHttpClient.builder()
                    .connectTimeout(properties.getConnectTimeout())
                    .readTimeout(properties.getReadTimeout())
                    .build();
            return sharedClient.compareAndSet(null, created) ? created : sharedClient.get();
        }
    }
}

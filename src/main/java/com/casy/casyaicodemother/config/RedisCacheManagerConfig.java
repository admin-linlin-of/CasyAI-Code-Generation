package com.casy.casyaicodemother.config;

import jakarta.annotation.Resource;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.cfg.DateTimeFeature;

import java.time.Duration;

/**
 * Spring Cache 使用 Redis 作为后端时的 {@link CacheManager} 配置。
 * <p>
 * 序列化使用 Jackson 3（{@code tools.jackson.*}）。{@code java.time} 已内置，无需再 register JavaTimeModule。
 * </p>
 * <p>
 * 必须开启 default typing：否则 {@code @Cacheable} 读回的是 {@code LinkedHashMap}，
 * 无法转成 {@code BaseResponse} / {@code Page} / {@code AppVO}，会报 ClassCastException。
 * 仅用于本服务内部 Redis，使用 {@link GenericJacksonJsonRedisSerializer.GenericJacksonJsonRedisSerializerBuilder#enableUnsafeDefaultTyping()}。
 * </p>
 */
@Configuration
public class RedisCacheManagerConfig {

    @Resource
    private RedisConnectionFactory redisConnectionFactory;

    @Bean
    public CacheManager cacheManager() {
        // JSON 里写入 @class 类型信息，反序列化才能还原 Page、AppVO 等具体类型
        GenericJacksonJsonRedisSerializer valueSerializer = GenericJacksonJsonRedisSerializer.builder()
                .customize(mapper -> mapper.disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS))
                .enableUnsafeDefaultTyping() // JSON 带 @class 才能正确的反序列化
                .build();

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30))
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(valueSerializer));

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(defaultConfig)
                .withCacheConfiguration("good_app_page",
                        defaultConfig.entryTtl(Duration.ofMinutes(5)))
                .build();
    }
}

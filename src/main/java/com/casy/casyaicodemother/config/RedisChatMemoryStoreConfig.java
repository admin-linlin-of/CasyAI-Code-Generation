package com.casy.casyaicodemother.config;

import cn.hutool.core.util.StrUtil;
import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * langchain的Redis对话记忆存储配置类
 *
 */
@Configuration
@ConfigurationProperties(prefix = "spring.data.redis")
@Data
public class RedisChatMemoryStoreConfig {

    private String host;

    private int port;

    private String username;

    private String password;

    private long ttl;

    @Bean
    public ChatMemoryStore redisChatMemoryStore() {
        RedisChatMemoryStore.Builder builder = RedisChatMemoryStore.builder()
                .host(host)
                .port(port)
                .user(username)
                .password(password)
                .ttl(ttl);
        // 有密码时必填用户名
        if (StrUtil.isNotBlank(password)) {
            builder.user("default");
        }
        // 过滤 thinking 模型产生的空 assistant，避免质检重试时被 DeepSeek 拒绝
        return new SanitizingChatMemoryStore(builder.build());
    }
}

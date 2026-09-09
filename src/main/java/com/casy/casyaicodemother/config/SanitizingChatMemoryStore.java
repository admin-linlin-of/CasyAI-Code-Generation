package com.casy.casyaicodemother.config;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Redis 对话记忆过滤器。
 * <p>
 * 思考模型常先写出「无 content、无 tool_calls」的 assistant（reasoning 在 thinking()）。
 * 若直接丢弃，Vue 的 writeFile 循环会在这一轮被掐断，表现为最终响应 null。
 * 有 thinking 时补一个占位正文，满足 DeepSeek「content 或 tool_calls 必填」。
 */
@Slf4j
@RequiredArgsConstructor
public class SanitizingChatMemoryStore implements ChatMemoryStore {

    private final ChatMemoryStore delegate;

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        return sanitize(delegate.getMessages(memoryId), memoryId, false);
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        delegate.updateMessages(memoryId, sanitize(messages, memoryId, true));
    }

    @Override
    public void deleteMessages(Object memoryId) {
        delegate.deleteMessages(memoryId);
    }

    private List<ChatMessage> sanitize(List<ChatMessage> messages, Object memoryId, boolean persist) {
        if (CollUtil.isEmpty(messages)) {
            return messages == null ? List.of() : messages;
        }
        List<ChatMessage> cleaned = new ArrayList<>(messages.size());
        int dropped = 0;
        int thinkingPlaceholder = 0;
        for (ChatMessage message : messages) {
            if (!(message instanceof AiMessage aiMessage)) {
                cleaned.add(message);
                continue;
            }
            boolean noText = StrUtil.isBlank(aiMessage.text());
            boolean noTools = !aiMessage.hasToolExecutionRequests();
            if (!noText || !noTools) {
                cleaned.add(aiMessage);
                continue;
            }
            if (StrUtil.isNotBlank(aiMessage.thinking())) {
                // 空格只进 Redis 记忆，不会写入 t_chat_history / 聊天气泡
                cleaned.add(aiMessage.withText(" "));
                thinkingPlaceholder++;
                continue;
            }
            dropped++;
        }
        if (thinkingPlaceholder > 0) {
            log.info("已为 {} 条仅-thinking 的 assistant 补占位正文, memoryId={}, persist={}",
                    thinkingPlaceholder, memoryId, persist);
        }
        if (dropped > 0) {
            log.warn("已丢弃 {} 条空 assistant 消息, memoryId={}, persist={}", dropped, memoryId, persist);
        }
        return cleaned;
    }
}

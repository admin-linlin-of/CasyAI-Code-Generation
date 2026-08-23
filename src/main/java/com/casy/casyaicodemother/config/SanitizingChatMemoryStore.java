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
 * 过滤无效 assistant 消息：thinking 模型常写出无 content、无 tool_calls 的空 AiMessage，
 * DeepSeek 再次请求时会报 Invalid assistant message: content or tool_calls must be set。
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
        for (ChatMessage message : messages) {
            if (isInvalidAssistant(message)) {
                dropped++;
                continue;
            }
            cleaned.add(message);
        }
        if (dropped > 0) {
            log.warn("已丢弃 {} 条空 assistant 消息, memoryId={}, persist={}", dropped, memoryId, persist);
        }
        return cleaned;
    }

    /**
     * assistant 既没有文本也没有工具调用时，OpenAI 兼容接口会拒绝该消息。
     */
    private static boolean isInvalidAssistant(ChatMessage message) {
        if (!(message instanceof AiMessage aiMessage)) {
            return false;
        }
        boolean noText = StrUtil.isBlank(aiMessage.text());
        boolean noTools = !aiMessage.hasToolExecutionRequests();
        return noText && noTools;
    }
}

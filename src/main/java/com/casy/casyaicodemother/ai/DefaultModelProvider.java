package com.casy.casyaicodemother.ai;

import com.casy.casyaicodemother.model.enums.ModelTypeEnum;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import org.springframework.beans.factory.ObjectProvider;

/**
 * {@link ModelProvider} 的通用实现。
 * <p>
 * ChatModel / StreamingChatModel 是 prototype：每次 {@link #getChatModel()} /
 * {@link #getStreamingChatModel()} 向容器要一个新实例。
 * 不能在构造时把模型拿死，否则会退回单例，多路工作流再次串在同一次 HTTP execute 上。
 */
public final class DefaultModelProvider implements ModelProvider {

    private final ModelTypeEnum type;
    private final ObjectProvider<ChatModel> chatModel;
    private final ObjectProvider<StreamingChatModel> streamingChatModel;

    public DefaultModelProvider(ModelTypeEnum type,
                                ObjectProvider<ChatModel> chatModel,
                                ObjectProvider<StreamingChatModel> streamingChatModel) {
        this.type = type;
        this.chatModel = chatModel;
        this.streamingChatModel = streamingChatModel;
    }

    @Override
    public ModelTypeEnum getType() {
        return type;
    }

    @Override
    public ChatModel getChatModel() {
        return chatModel.getObject();
    }

    @Override
    public StreamingChatModel getStreamingChatModel() {
        return streamingChatModel.getObject();
    }
}

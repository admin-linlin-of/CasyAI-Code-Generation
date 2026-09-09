package com.casy.casyaicodemother.ai;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HTML / 多文件关闭模型思考。
 * <p>
 * 这两类生成的产物就是 assistant 正文（再解析成 html/css/js）。思考一开，
 * reasoning 往往把 max-tokens 占满，content 为空，解析必然失败。
 * Vue 工程不要走这里：它的产物是 tool_calls，思考仍有用。
 */
public final class ThinkingDisabledChatModels {

    /** DeepSeek / OpenAI 兼容网关：显式关掉 thinking，避免沿用模型默认的 enabled */
    private static final Map<String, Object> THINKING_OFF = Map.of("thinking", Map.of("type", "disabled"));

    private ThinkingDisabledChatModels() {
    }

    public static ChatModel wrap(ChatModel delegate) {
        return new DisabledChatModel(delegate);
    }

    public static StreamingChatModel wrap(StreamingChatModel delegate) {
        return new DisabledStreamingChatModel(delegate);
    }

    /**
     * 在单次请求参数上覆盖 thinking=disabled。
     * 只改本次 ChatRequest，不改共享的模型 Bean，因此不影响同时进行的 Vue 对话。
     */
    static ChatRequest disableThinking(ChatRequest request) {
        if (request == null) {
            return null;
        }
        ChatRequestParameters parameters = request.parameters();
        if (!(parameters instanceof OpenAiChatRequestParameters openAi)) {
            return request;
        }
        Map<String, Object> custom = new HashMap<>();
        if (openAi.customParameters() != null) {
            custom.putAll(openAi.customParameters());
        }
        custom.putAll(THINKING_OFF);
        OpenAiChatRequestParameters patched = OpenAiChatRequestParameters.builder()
                .overrideWith(openAi)
                .customParameters(custom)
                .build();
        return request.toBuilder().parameters(patched).build();
    }

    private record DisabledChatModel(ChatModel delegate) implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest request) {
            return delegate.doChat(disableThinking(request));
        }

        @Override
        public ChatRequestParameters defaultRequestParameters() {
            return delegate.defaultRequestParameters();
        }

        @Override
        public List<ChatModelListener> listeners() {
            return delegate.listeners();
        }
    }

    private record DisabledStreamingChatModel(StreamingChatModel delegate) implements StreamingChatModel {
        @Override
        public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
            delegate.doChat(disableThinking(request), handler);
        }

        @Override
        public ChatRequestParameters defaultRequestParameters() {
            return delegate.defaultRequestParameters();
        }

        @Override
        public List<ChatModelListener> listeners() {
            return delegate.listeners();
        }
    }
}

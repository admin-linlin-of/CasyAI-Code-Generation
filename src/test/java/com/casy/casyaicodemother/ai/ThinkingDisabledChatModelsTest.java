package com.casy.casyaicodemother.ai;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ThinkingDisabledChatModelsTest {

    @Test
    void disableThinkingOverridesCustomParameter() {
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(UserMessage.from("hi")))
                .parameters(OpenAiChatRequestParameters.builder()
                        .customParameters(Map.of("thinking", Map.of("type", "enabled")))
                        .build())
                .build();
        ChatRequest patched = ThinkingDisabledChatModels.disableThinking(request);
        OpenAiChatRequestParameters parameters = (OpenAiChatRequestParameters) patched.parameters();
        @SuppressWarnings("unchecked")
        Map<String, Object> thinking = (Map<String, Object>) parameters.customParameters().get("thinking");
        assertEquals("disabled", thinking.get("type"));
    }
}

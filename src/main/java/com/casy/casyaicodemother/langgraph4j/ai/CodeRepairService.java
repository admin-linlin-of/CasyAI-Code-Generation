package com.casy.casyaicodemother.langgraph4j.ai;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;

/**
 * 代码修复 AI 服务，与代码生成服务分离，使用独立提示词和独立对话记忆。
 */
public interface CodeRepairService {

    @SystemMessage(fromResource = "prompt/code-repair-system-prompt.txt")
    TokenStream repairCode(@MemoryId long appId, @UserMessage String userMessage);
}

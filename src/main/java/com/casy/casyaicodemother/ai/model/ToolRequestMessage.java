package com.casy.casyaicodemother.ai.model;

import com.casy.casyaicodemother.model.enums.StreamMessageTypeEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 工具调用请求消息（流式阶段）
 * <p>
 * 对应 TokenStream.onPartialToolCall 回调，LLM 决定调用工具后以流式方式输出请求信息。
 * 同一工具调用会多次推送，前端需根据 index 聚合 partialArguments 片段。
 * 完整请求信息在 onToolExecuted 的 ToolExecutedMessage 中可再次获取。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
public class ToolRequestMessage extends StreamMessage {

    /**
     * 工具调用序号，从 0 开始递增。
     * LLM 一次返回多个工具调用时，用 index 区分并关联同一工具的流式片段。
     */
    private Integer index;

    /**
     * 工具调用唯一 ID，由 LLM 提供商生成。
     * 部分提供商（如 Google、Ollama）可能不返回此字段。
     */
    private String id;

    /**
     * 被调用的工具名称，对应 @Tool 注解的方法名，如 writeFile。
     */
    private String name;

    /**
     * 工具参数 JSON 片段。
     * 流式推送时为 partialArguments，所有片段拼接后应形成完整 JSON，如 {"relativeFilePath":"src/App.vue","content":"..."}。
     */
    private String arguments;

    /**
     * 是否为流式片段。true 表示参数尚未完整，false 表示参数已完整（当前仅流式阶段会推送 true）。
     */
    private Boolean partial;

    public ToolRequestMessage(Integer index, String id, String name, String arguments, Boolean partial) {
        super(StreamMessageTypeEnum.TOOL_REQUEST.getValue());
        this.index = index;
        this.id = id;
        this.name = name;
        this.arguments = arguments;
        this.partial = partial;
    }
}

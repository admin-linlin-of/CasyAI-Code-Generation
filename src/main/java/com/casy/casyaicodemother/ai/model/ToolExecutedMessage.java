package com.casy.casyaicodemother.ai.model;

import com.casy.casyaicodemother.model.enums.StreamMessageTypeEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 工具执行结果消息
 * <p>
 * 对应 TokenStream.onToolExecuted 回调，AI Service 自动执行完 @Tool 方法后推送。
 * 包含完整的工具调用请求与执行返回值，前端可据此展示工具执行状态（如文件写入成功）。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
public class ToolExecutedMessage extends StreamMessage {

    /**
     * 工具调用唯一 ID，与 ToolRequestMessage.id 对应。
     */
    private String id;

    /**
     * 被调用的工具名称。
     */
    private String name;

    /**
     * 完整的工具参数 JSON 字符串，如 {"relativeFilePath":"src/App.vue","content":"..."}。
     */
    private String arguments;

    /**
     * 工具执行返回值文本。
     * String 类型直接返回；其他类型序列化为 JSON；void 返回 "Success"；执行失败时为错误描述。
     */
    private String result;

    /**
     * 工具执行是否失败。true 表示执行出错，result 中为错误信息。
     */
    private Boolean failed;

    public ToolExecutedMessage(String id, String name, String arguments, String result, Boolean failed) {
        super(StreamMessageTypeEnum.TOOL_EXECUTED.getValue());
        this.id = id;
        this.name = name;
        this.arguments = arguments;
        this.result = result;
        this.failed = failed;
    }
}

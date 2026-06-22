package com.casy.casyaicodemother.ai.model;

import com.casy.casyaicodemother.model.enums.StreamMessageTypeEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * AI 深度思考消息（reasoning / thinking 流式片段）
 */
@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
public class AiThinkingMessage extends StreamMessage {

    /** 思考内容 token 片段 */
    private String data;

    public AiThinkingMessage(String data) {
        super(StreamMessageTypeEnum.AI_THINKING.getValue());
        this.data = data;
    }
}

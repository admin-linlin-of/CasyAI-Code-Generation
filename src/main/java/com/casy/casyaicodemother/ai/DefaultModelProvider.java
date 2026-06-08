package com.casy.casyaicodemother.ai;

import com.casy.casyaicodemother.model.enums.ModelTypeEnum;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;

/**
 * {@link ModelProvider} 的通用实现（具体策略 / ConcreteStrategy）。
 * <p>
 * 将「模型类型 + 普通模型 + 流式模型」三个对象打包为一个策略实例，
 * 供 {@link AiCodeGeneratorServiceFactory} 统一消费。
 * <p>
 * 命名说明：Default 表示「默认的通用实现」，并非「系统默认模型」；
 * 系统默认模型为 {@link ModelTypeEnum#DEEPSEEKFLASH}，在 Factory 的 {@code aiCodeGeneratorService()} Bean 中指定。
 * <p>
 * 为什么使用 record：
 * <ul>
 *   <li>record 是 Java 16+ 的数据载体语法，适合「只存数据、无复杂逻辑」的类</li>
 *   <li>编译器自动生成构造器、equals、hashCode、toString</li>
 *   <li>record 默认生成 {@code type()} 而非 {@code getType()}，因此需手动实现接口的 getter 方法</li>
 * </ul>
 * <p>
 * 使用示例（在各 *ModelConfig 中）：
 * <pre>{@code
 * return new DefaultModelProvider(ModelTypeEnum.GPT, chatModel, streamingChatModel);
 * }</pre>
 *
 * @param type               模型类型
 * @param chatModel          普通对话模型
 * @param streamingChatModel 流式对话模型
 */
public record DefaultModelProvider(
        ModelTypeEnum type,
        ChatModel chatModel,
        StreamingChatModel streamingChatModel
) implements ModelProvider {

    @Override
    public ModelTypeEnum getType() {
        return type;
    }

    @Override
    public ChatModel getChatModel() {
        return chatModel;
    }

    @Override
    public StreamingChatModel getStreamingChatModel() {
        return streamingChatModel;
    }
}

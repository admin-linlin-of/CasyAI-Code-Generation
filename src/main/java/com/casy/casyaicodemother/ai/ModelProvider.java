package com.casy.casyaicodemother.ai;

import com.casy.casyaicodemother.model.enums.ModelTypeEnum;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;

/**
 * AI 模型策略接口（策略模式 - Strategy）。
 * <p>
 * 设计目标：符合开闭原则（OCP）——新增模型时只需新增配置类并注册 Bean，
 * 无需修改 {@link AiCodeGeneratorServiceFactory}。
 * <p>
 * 角色对应关系：
 * <ul>
 *   <li>{@link ModelProvider} — 策略接口（Strategy），定义「一种模型能提供什么」</li>
 *   <li>{@link DefaultModelProvider} — 具体策略（ConcreteStrategy），封装某一模型的 ChatModel 实例</li>
 *   <li>各 *ModelConfig — 策略注册方，负责创建并注入 Spring 容器</li>
 *   <li>{@link AiCodeGeneratorServiceFactory} — 上下文（Context），按模型类型选择策略并构建 AI 服务</li>
 * </ul>
 * <p>
 * 新增模型步骤（以 Xxx 为例）：
 * <ol>
 *   <li>在 {@link ModelTypeEnum} 增加枚举值</li>
 *   <li>新增 XxxModelProperties + XxxModelConfig，创建 ChatModel / StreamingChatModel Bean</li>
 *   <li>在 XxxModelConfig 中注册 {@code @Bean ModelProvider}，返回 {@link DefaultModelProvider}</li>
 * </ol>
 *
 * @see DefaultModelProvider
 * @see AiCodeGeneratorServiceFactory
 */
public interface ModelProvider {

    /**
     * 模型类型标识，与 {@link ModelTypeEnum} 一一对应，作为策略选择的 key。
     */
    ModelTypeEnum getType();

    /**
     * 普通（非流式）对话模型。
     */
    ChatModel getChatModel();

    /**
     * 流式对话模型。
     */
    StreamingChatModel getStreamingChatModel();
}

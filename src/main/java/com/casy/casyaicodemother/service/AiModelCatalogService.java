package com.casy.casyaicodemother.service;

import com.casy.casyaicodemother.model.entity.AiModel;
import com.casy.casyaicodemother.model.enums.ModelTypeEnum;
import com.mybatisflex.core.service.IService;

import java.util.List;

/**
 * AI 模型目录：从表读取当前可用模型，供路由 prompt、指定模型校验、失败回退使用。
 */
public interface AiModelCatalogService extends IService<AiModel> {

    /** 仅 enabled=1，按 sort_order 升序；一个都没有则抛错 */
    List<AiModel> listEnabled();

    /** 全部模型（含停用），给管理列表用 */
    List<AiModel> listAll();

    /** 当前默认模型：优先 is_default=1，否则取启用列表第一条 */
    ModelTypeEnum getDefaultEnum();

    /**
     * 用户明确指定模型时校验。
     *
     * @param modelCodeOrName 枚举名（GPT）或调用名（gpt-5.5）
     */
    ModelTypeEnum requireEnabled(String modelCodeOrName);

    /**
     * 路由结果二次校验：选中已停用或为空时回退默认模型。
     * 用于 AI 仍可能 hallucinate 出 prompt 里没有的枚举。
     */
    ModelTypeEnum resolveEnabledOrDefault(ModelTypeEnum routed);

    /** 用当前启用模型填充 codegen-routing-model-prompt.txt 占位符 */
    String buildRoutingPrompt();

    /**
     * 开关模型。不允许把最后一个可用模型关掉。
     *
     * @param enabled 1 启用，0 停用
     */
    boolean updateEnabled(Long id, Integer enabled);
}

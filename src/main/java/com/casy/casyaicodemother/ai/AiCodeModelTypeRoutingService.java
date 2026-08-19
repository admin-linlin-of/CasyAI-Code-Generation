package com.casy.casyaicodemother.ai;

import com.casy.casyaicodemother.model.enums.ModelTypeEnum;

/**
 * 按用户需求选择代码生成模型。
 * <p>
 * System prompt 不再写死在注解里，由工厂每次调用前从 t_ai_model 拼装，
 * 这样停用 GPT 后路由模型看不到该选项。
 */
public interface AiCodeModelTypeRoutingService {

    ModelTypeEnum routeCodeModelType(String userPrompt);
}

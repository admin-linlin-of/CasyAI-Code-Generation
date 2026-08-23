package com.casy.casyaicodemother.langgraph4j.node;

import com.casy.casyaicodemother.ai.AiCodeModelTypeRoutingService;
import com.casy.casyaicodemother.langgraph4j.state.WorkflowContext;
import com.casy.casyaicodemother.model.enums.ModelTypeEnum;
import com.casy.casyaicodemother.service.AiModelCatalogService;
import com.casy.casyaicodemother.util.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Slf4j
public class CodeGenModelRouterNode {

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            log.info("执行节点: 智能选择代码生成模型");
            AiModelCatalogService catalog = SpringContextUtil.getBean(AiModelCatalogService.class);

            // 首页已选手动模型则校验启用后直接使用，不再重复路由
            if (context.getModelTypeEnum() != null) {
                ModelTypeEnum specified = catalog.requireEnabled(context.getModelTypeEnum().getModelName());
                context.setModelTypeEnum(specified);
                context.setCurrentStep("智能选择代码生成模型");
                log.info("已指定代码生成模型，跳过智能路由: {}", specified.getModelName());
                return WorkflowContext.saveContext(context);
            }

            ModelTypeEnum modelTypeEnum;
            try {
                AiCodeModelTypeRoutingService routingService = SpringContextUtil.getBean(AiCodeModelTypeRoutingService.class);
                // 先让路由 AI 从「当前启用列表」里选，再校验一次，防止返回已停用枚举
                modelTypeEnum = catalog.resolveEnabledOrDefault(
                        routingService.routeCodeModelType(context.getOriginalPrompt()));
                log.info("AI智能模型路由完成，选择类型: {}", modelTypeEnum.getModelName());
            } catch (Exception e) {
                modelTypeEnum = catalog.getDefaultEnum();
                log.error("AI智能模型路由失败，回退默认模型 {}: {}", modelTypeEnum.getModelName(), e.getMessage());
            }

            context.setCurrentStep("智能选择代码生成模型");
            context.setModelTypeEnum(modelTypeEnum);
            return WorkflowContext.saveContext(context);
        });
    }
}

package com.casy.casyaicodemother.langgraph4j.node;

import com.casy.casyaicodemother.ai.AiCodeModelTypeRoutingService;
import com.casy.casyaicodemother.langgraph4j.state.WorkflowContext;
import com.casy.casyaicodemother.model.enums.ModelTypeEnum;
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
            log.info("执行节点: 智能选择代码生成类型");

            ModelTypeEnum modelTypeEnum;
            try {
                // 获取AI路由服务
                AiCodeModelTypeRoutingService routingService = SpringContextUtil.getBean(AiCodeModelTypeRoutingService.class);
                // 根据原始提示词进行智能路由（智能选择代码生成类型）
                modelTypeEnum = routingService.routeCodeModelType(context.getOriginalPrompt());
                log.info("AI智能模型路由完成，选择类型: {}", modelTypeEnum.getModelName());
            } catch (Exception e) {
                log.error("AI智能模型路由失败，使用默认deepseek-v4-flash模型: {}", e.getMessage());
                modelTypeEnum = ModelTypeEnum.DEEPSEEKFLASH;
            }

            // 更新状态
            context.setCurrentStep("智能选择代码生成类型");
            context.setModelTypeEnum(modelTypeEnum);
            return WorkflowContext.saveContext(context);
        });
    }
}


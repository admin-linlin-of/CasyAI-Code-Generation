package com.casy.casyaicodemother.langgraph4j.node;

import com.casy.casyaicodemother.ai.AiCodeGenTypeRoutingService;
import com.casy.casyaicodemother.langgraph4j.state.WorkflowContext;
import com.casy.casyaicodemother.model.enums.CodeGenTypeEnum;
import com.casy.casyaicodemother.util.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Slf4j
public class CodeGenTypeRouterNode {

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            log.info("执行节点: 智能选择代码生成类型");

            // 首页已选手动类型，或创建/加载应用时已写入，不再重复路由
            if (context.getGenerationType() != null) {
                log.info("已指定代码生成类型，跳过智能路由: {} ({})",
                        context.getGenerationType().getValue(), context.getGenerationType().getText());
                context.setCurrentStep("智能选择代码生成类型");
                return WorkflowContext.saveContext(context);
            }

            CodeGenTypeEnum generationType;
            try {
                // 获取AI路由服务
                AiCodeGenTypeRoutingService routingService = SpringContextUtil.getBean(AiCodeGenTypeRoutingService.class);
                // 根据原始提示词进行智能路由（智能选择代码生成类型）
                generationType = routingService.routeCodeGenType(context.getOriginalPrompt());
                log.info("AI智能路由完成，选择类型: {} ({})", generationType.getValue(), generationType.getText());
            } catch (Exception e) {
                log.error("AI智能路由失败，使用默认HTML类型: {}", e.getMessage());
                generationType = CodeGenTypeEnum.HTML;
            }

            // 更新状态
            context.setCurrentStep("智能选择代码生成类型");
            context.setGenerationType(generationType);
            return WorkflowContext.saveContext(context);
        });
    }
}


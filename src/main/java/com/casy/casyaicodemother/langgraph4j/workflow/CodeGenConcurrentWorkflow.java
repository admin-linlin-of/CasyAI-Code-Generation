package com.casy.casyaicodemother.langgraph4j.workflow;

import cn.hutool.json.JSONUtil;
import com.casy.casyaicodemother.exception.BusinessException;
import com.casy.casyaicodemother.exception.ErrorCode;
import com.casy.casyaicodemother.langgraph4j.model.QualityResult;
import com.casy.casyaicodemother.langgraph4j.node.*;
import com.casy.casyaicodemother.langgraph4j.node.concurrent.*;
import com.casy.casyaicodemother.langgraph4j.state.WorkflowContext;
import com.casy.casyaicodemother.model.enums.CodeGenTypeEnum;
import com.casy.casyaicodemother.model.enums.ModelTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphRepresentation;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.prebuilt.MessagesStateGraph;
import reactor.core.publisher.Flux;

import java.util.Map;

import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;

@Slf4j
public class CodeGenConcurrentWorkflow {

    /**
     * 创建并发工作流
     */
    public CompiledGraph<MessagesState<String>> createWorkflow() {
        try {
            return new MessagesStateGraph<String>()
                    // 添加节点：对齐首页发起会话，无 appId 先创建应用
                    .addNode("app_prepare", AppPrepareNode.create())
                    .addNode("image_plan", ImagePlanNode.create())
                    .addNode("prompt_enhancer", PromptEnhancerNode.create())
                    .addNode("code_gen_type_router", CodeGenTypeRouterNode.create())
                    .addNode("code_gen_model_router", CodeGenModelRouterNode.create())
                    // 对齐 chatToGenCode：生成前先写入用户消息
                    .addNode("save_chat_history", ChatHistorySaveNode.create())
                    .addNode("code_generator", CodeGeneratorNode.create())
                    .addNode("code_repair", CodeRepairNode.create())
                    .addNode("code_quality_check", CodeQualityCheckNode.create())
                    .addNode("project_builder", ProjectBuilderNode.create())

                    // 添加并发图片收集节点
                    .addNode("content_image_collector", ContentImageCollectorNode.create())
                    .addNode("illustration_collector", IllustrationCollectorNode.create())
                    .addNode("diagram_collector", DiagramCollectorNode.create())
                    .addNode("logo_collector", LogoCollectorNode.create())
                    .addNode("image_aggregator", ImageAggregatorNode.create())

                    // 添加边：先准备应用，再进入图片计划
                    .addEdge(START, "app_prepare")
                    .addEdge("app_prepare", "image_plan")

                    // 并发分支：从计划节点分发到各个收集节点
                    .addEdge("image_plan", "content_image_collector")
                    .addEdge("image_plan", "illustration_collector")
                    .addEdge("image_plan", "diagram_collector")
                    .addEdge("image_plan", "logo_collector")

                    // 汇聚：所有收集节点都汇聚到聚合器
                    .addEdge("content_image_collector", "image_aggregator")
                    .addEdge("illustration_collector", "image_aggregator")
                    .addEdge("diagram_collector", "image_aggregator")
                    .addEdge("logo_collector", "image_aggregator")

                    // 继续串行流程
                    .addEdge("image_aggregator", "prompt_enhancer")
                    .addEdge("prompt_enhancer", "code_gen_type_router")
                    .addEdge("code_gen_type_router", "code_gen_model_router")
                    .addEdge("code_gen_model_router", "save_chat_history")
                    .addEdge("save_chat_history", "code_generator")
                    .addEdge("code_generator", "code_quality_check")
                    .addEdge("code_repair", "code_quality_check")

                    // 质检条件边：失败走修复节点，满 3 次则放弃
                    .addConditionalEdges("code_quality_check",
                            edge_async(this::routeAfterQualityCheck),
                            Map.of(
                                    "build", "project_builder",
                                    "skip_build", END,
                                    "repair", "code_repair"
                            ))
                    // 打包失败走修复节点，满 3 次结束
                    .addConditionalEdges("project_builder",
                            edge_async(this::routeAfterBuild),
                            Map.of(
                                    "ok", END,
                                    "repair", "code_repair"
                            ))
                    .compile();
        } catch (GraphStateException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "并发工作流创建失败");
        }
    }

    /**
     * 执行并发工作流（无 appId 时由 app_prepare 节点创建应用）
     */
    public WorkflowContext executeWorkflow(String originalPrompt) {
        return executeWorkflow(originalPrompt, null, null, null, null);
    }

    /**
     * 执行并发工作流
     *
     * @param originalPrompt 用户原始需求
     * @param appId          已有应用 ID，为空则先创建应用
     */
    public WorkflowContext executeWorkflow(String originalPrompt, Long appId) {
        return executeWorkflow(originalPrompt, appId, null, null, null);
    }

    /**
     * 执行并发工作流（对齐首页：可选指定用户、生成类型、模型）
     *
     * @param originalPrompt 用户原始需求
     * @param appId          已有应用 ID，为空则先创建应用
     * @param userId         创建应用所用用户，为空则取库中第一个用户
     * @param generationType 首页下拉已选生成类型，为空则 AI 路由
     * @param modelTypeEnum  首页下拉已选模型，为空则 AI 路由
     */
    public WorkflowContext executeWorkflow(String originalPrompt, Long appId, Long userId,
                                           CodeGenTypeEnum generationType, ModelTypeEnum modelTypeEnum) {
        CompiledGraph<MessagesState<String>> workflow = createWorkflow();
        WorkflowContext initialContext = WorkflowContext.builder()
                .originalPrompt(originalPrompt)
                .currentStep("初始化")
                .appId(appId)
                .userId(userId)
                .generationType(generationType)
                .modelTypeEnum(modelTypeEnum)
                .build();
        GraphRepresentation graph = workflow.getGraph(GraphRepresentation.Type.MERMAID);
        log.info("并发工作流图:\n{}", graph.content());
        log.info("开始执行并发代码生成工作流");
        WorkflowContext finalContext = null;
        int stepCounter = 1;
        for (NodeOutput<MessagesState<String>> step : workflow.stream(
                Map.of(WorkflowContext.WORKFLOW_CONTEXT_KEY, initialContext)
        )) {
            log.info("--- 第 {} 步完成 ---", stepCounter);
            WorkflowContext currentContext = WorkflowContext.getContext(step.state());
            if (currentContext != null) {
                finalContext = currentContext;
                log.info("当前步骤上下文: {}", currentContext);
            }
            stepCounter++;
        }
        log.info("并发代码生成工作流执行完成！");
        return finalContext;
    }



    /**
     * 执行工作流（Flux 流式输出版本）
     */
    public Flux<String> executeWorkflowWithFlux(String originalPrompt) {
        return Flux.create(sink -> {
            Thread.startVirtualThread(() -> {
                try {
                    CompiledGraph<MessagesState<String>> workflow = createWorkflow();
                    WorkflowContext initialContext = WorkflowContext.builder()
                            .originalPrompt(originalPrompt)
                            .currentStep("初始化")
                            .build();
                    sink.next(formatSseEvent("workflow_start", Map.of(
                            "message", "开始执行代码生成工作流",
                            "originalPrompt", originalPrompt
                    )));
                    GraphRepresentation graph = workflow.getGraph(GraphRepresentation.Type.MERMAID);
                    log.info("工作流图:\n{}", graph.content());

                    int stepCounter = 1;
                    for (NodeOutput<MessagesState<String>> step : workflow.stream(
                            Map.of(WorkflowContext.WORKFLOW_CONTEXT_KEY, initialContext))) {
                        log.info("--- 第 {} 步完成 ---", stepCounter);
                        WorkflowContext currentContext = WorkflowContext.getContext(step.state());
                        if (currentContext != null) {
                            sink.next(formatSseEvent("step_completed", Map.of(
                                    "stepNumber", stepCounter,
                                    "currentStep", currentContext.getCurrentStep()
                            )));
                            log.info("当前步骤上下文: {}", currentContext);
                        }
                        stepCounter++;
                    }
                    sink.next(formatSseEvent("workflow_completed", Map.of(
                            "message", "代码生成工作流执行完成！"
                    )));
                    log.info("代码生成工作流执行完成！");
                    sink.complete();
                } catch (Exception e) {
                    log.error("工作流执行失败: {}", e.getMessage(), e);
                    sink.next(formatSseEvent("workflow_error", Map.of(
                            "error", e.getMessage(),
                            "message", "工作流执行失败"
                    )));
                    sink.error(e);
                }
            });
        });
    }

    /**
     * 格式化 SSE 事件的辅助方法
     */
    private String formatSseEvent(String eventType, Object data) {
        try {
            String jsonData = JSONUtil.toJsonStr(data);
            return "event: " + eventType + "\ndata: " + jsonData + "\n\n";
        } catch (Exception e) {
            log.error("格式化 SSE 事件失败: {}", e.getMessage(), e);
            return "event: error\ndata: {\"error\":\"格式化失败\"}\n\n";
        }
    }


    /**
     * 路由函数：根据质检结果决定下一步
     */
    private String routeAfterQualityCheck(MessagesState<String> state) {
        WorkflowContext context = WorkflowContext.getContext(state);
        QualityResult qualityResult = context.getQualityResult();

        if (qualityResult == null || !qualityResult.getIsValid()) {
            if (canRepair(context)) {
                log.error("代码质检失败，进入修复节点");
                return "repair";
            }
            log.error("代码质检失败且已达修复上限 {} 次，结束流程", WorkflowContext.MAX_REPAIR_COUNT);
            return "skip_build";
        }
        log.info("代码质检通过，继续后续流程");
        CodeGenTypeEnum generationType = context.getGenerationType();
        if (generationType == CodeGenTypeEnum.VUE_PROJECT) {
            return "build";
        } else {
            return "skip_build";
        }
    }

    /**
     * 打包节点结束后：成功结束；失败且未满 3 次则修复。
     */
    private String routeAfterBuild(MessagesState<String> state) {
        WorkflowContext context = WorkflowContext.getContext(state);
        QualityResult qualityResult = context.getQualityResult();
        if (qualityResult != null && Boolean.FALSE.equals(qualityResult.getIsValid())) {
            if (canRepair(context)) {
                log.error("Vue 项目构建失败，进入修复节点");
                return "repair";
            }
            log.error("Vue 项目构建失败且已达修复上限 {} 次，结束流程", WorkflowContext.MAX_REPAIR_COUNT);
            return "ok";
        }
        return "ok";
    }

    private boolean canRepair(WorkflowContext context) {
        Integer count = context.getRepairCount();
        return count == null || count < WorkflowContext.MAX_REPAIR_COUNT;
    }
}

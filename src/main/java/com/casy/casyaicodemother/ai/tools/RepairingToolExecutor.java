package com.casy.casyaicodemother.ai.tools;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;

/**
 * 带 JSON 容错能力的 {@link ToolExecutor} 装饰器（Decorator）。
 * <p>
 * <b>注册方式</b>：在 {@link com.casy.casyaicodemother.ai.AiCodeGeneratorServiceFactory#wrapToolsWithRepair(Object)}
 * 中，通过 {@link dev.langchain4j.service.tool.ToolService#findTools(Object)} 扫描 {@link FileWriteTool} 的 @Tool 方法，
 * 取得 langchain4j 自动生成的 {@link dev.langchain4j.service.tool.DefaultToolExecutor} 后，用本类包装并注册到 AiServices。
 * <p>
 * <b>结构示意</b>：
 * <pre>
 * langchain4j ToolService
 *     └── RepairingToolExecutor（本类，装饰层）
 *             └── DefaultToolExecutor（委托层，langchain4j 生成）
 *                     └── FileWriteTool.writeFile(...)（实际业务）
 * </pre>
 * <p>
 * <b>为何需要装饰器而非在 FileWriteTool 内修复</b>：
 * LLM 返回的 tool arguments 是 JSON 字符串，langchain4j 会在 {@code DefaultToolExecutor.prepareArguments()}
 * 阶段用 Jackson 解析为 Map，解析失败时异常在此抛出，根本到不了 {@link FileWriteTool}。
 * 因此必须在 {@link ToolExecutor} 层、Jackson 解析之前介入修复。
 * <p>
 * <b>完整执行顺序</b>（以 Vue 项目流式生成为例）：
 * <ol>
 *   <li>DeepSeek 返回 tool_call（name=writeFile, arguments=原始 JSON 字符串）</li>
 *   <li>{@code AiServiceStreamingResponseHandler} 触发工具执行</li>
 *   <li><b>本类 {@link #executeWithContext} 被调用</b></li>
 *   <li>{@link #repairRequest} → {@link ToolArgumentsJsonRepairer#repair} 尝试修复非法 JSON</li>
 *   <li>{@link #delegate}（DefaultToolExecutor）解析修复后的 arguments → 反射调用 {@link FileWriteTool#writeFile}</li>
 *   <li>若步骤 4/5 仍失败，{@code toolArgumentsErrorHandler} 将错误文本回传 LLM 重试（见 AiCodeGeneratorServiceFactory）</li>
 * </ol>
 *
 * @see ToolArgumentsJsonRepairer
 * @see com.casy.casyaicodemother.ai.AiCodeGeneratorServiceFactory#wrapToolsWithRepair(Object)
 */
public class RepairingToolExecutor implements ToolExecutor {

    /**
     * 被装饰的原始执行器，通常为 langchain4j 为 @Tool 方法生成的 DefaultToolExecutor。
     * 负责 Jackson 解析 arguments、参数类型转换、反射调用 FileWriteTool。
     */
    private final ToolExecutor delegate;

    /**
     * @param delegate langchain4j 原始 ToolExecutor，不可为 null
     */
    public RepairingToolExecutor(ToolExecutor delegate) {
        this.delegate = delegate;
    }

    /**
     * 带调用上下文的工具执行入口（langchain4j 流式生成走此方法）。
     * <p>
     * 执行顺序：{@code repairRequest(request)} → {@code delegate.executeWithContext(修复后的 request, context)}
     *
     * @param request LLM 返回的工具调用请求，含 id、name、arguments（JSON 字符串）
     * @param context 本次 AI 调用的上下文（memoryId、chatMemory 等）
     * @return 工具执行结果，最终会作为 ToolExecutionResultMessage 回传给 LLM
     */
    @Override
    public ToolExecutionResult executeWithContext(ToolExecutionRequest request, InvocationContext context) {
        return delegate.executeWithContext(repairRequest(request), context);
    }

    /**
     * 无上下文的工具执行入口（langchain4j 部分场景或兼容路径使用）。
     * <p>
     * 逻辑与 {@link #executeWithContext} 相同：先修复 arguments，再委托执行。
     *
     * @param request  LLM 返回的工具调用请求
     * @param memoryId 对话记忆 ID，对应 @ToolMemoryId 注入的 appId
     * @return 工具执行结果的字符串形式
     */
    @Override
    public String execute(ToolExecutionRequest request, Object memoryId) {
        return delegate.execute(repairRequest(request), memoryId);
    }

    /**
     * 在委托执行前修复 tool arguments JSON。
     * <p>
     * 步骤：
     * <ol>
     *   <li>读取 request.name()、request.arguments()</li>
     *   <li>调用 {@link ToolArgumentsJsonRepairer#repair}：合法 JSON 原样返回；非法则尝试按字段提取并重序列化</li>
     *   <li>若修复前后字符串相同 → 返回原 request（无需重建）</li>
     *   <li>若已修复 → {@code request.toBuilder().arguments(repairedArgs).build()} 生成新 request</li>
     * </ol>
     *
     * @param request LLM 原始 tool 调用请求
     * @return 修复后的 request；无法修复时返回原 request（后续由 DefaultToolExecutor 解析，失败则走 errorHandler）
     */
    private ToolExecutionRequest repairRequest(ToolExecutionRequest request) {
        String repairedArgs = ToolArgumentsJsonRepairer.repair(request.name(), request.arguments());
        if (repairedArgs.equals(request.arguments())) {
            return request;
        }
        return request.toBuilder().arguments(repairedArgs).build();
    }
}

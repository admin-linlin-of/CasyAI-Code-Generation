package com.casy.casyaicodemother.langgraph4j.state;

import cn.hutool.json.JSONUtil;
import com.casy.casyaicodemother.ai.model.ImageCollectionPlan;
import com.casy.casyaicodemother.langgraph4j.model.QualityResult;
import com.casy.casyaicodemother.model.enums.CodeGenTypeEnum;
import com.casy.casyaicodemother.model.enums.ModelTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 工作流上下文 - 存储所有状态信息
 */
@Slf4j
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowContext implements Serializable {

    /**
     * WorkflowContext 在 MessagesState 中的存储key
     */
    public static final String WORKFLOW_CONTEXT_KEY = "workflowContext";
    public static final int MAX_REPAIR_COUNT = 3;
    @Serial
    private static final long serialVersionUID = 1L;
    /**
     * 当前执行步骤
     */
    private String currentStep;
    /**
     * 用户原始输入的提示词
     */
    private String originalPrompt;
    /**
     * 图片资源列表
     */
    private List<ImageResource> imageList;
    /**
     * 增强后的提示词
     */
    private String enhancedPrompt;
    /**
     * 代码生成类型
     */
    private CodeGenTypeEnum generationType;
    /**
     * 代码生成模型
     */
    private ModelTypeEnum modelTypeEnum;
    /**
     * 真实应用 ID。代码保存、版本记录、Redis 对话记忆都依赖它，不能用 0 等占位值。
     * 为空时由 AppPrepareNode 按首页流程先创建应用。
     */
    private Long appId;
    /**
     * 当前用户 ID。创建应用、写入对话历史需要；为空时 AppPrepareNode 回退到库中第一个用户（方便测试）。
     */
    private Long userId;
    /**
     * 触发本次生成的用户消息 ID，写入 t_app_version.chat_history_id；测试场景可为空。
     */
    private Long userMessageId;
    /**
     * 生成的代码目录
     */
    private String generatedCodeDir;
    /**
     * 构建成功的目录
     */
    private String buildResultDir;
    /**
     * 错误信息
     */
    private String errorMessage;
    /**
     * 质量检查结果
     */
    private QualityResult qualityResult;
    /**
     * 代码修复次数，质检失败或打包失败会进入修复节点，上限见 {@link #MAX_REPAIR_COUNT}
     */
    private Integer repairCount;
    /**
     * 图片收集计划
     */
    private ImageCollectionPlan imageCollectionPlan;
    /**
     * 并发图片收集的中间结果字段
     */
    private List<ImageResource> contentImages;
    private List<ImageResource> illustrations;
    private List<ImageResource> diagrams;
    private List<ImageResource> logos;

    // ========== 上下文操作方法 ==========

    /**
     * 从 MessagesState 中获取 WorkflowContext。
     * DevTools 下 langgraph4j 会用 AppClassLoader 反序列化状态，得到的实例与 RestartClassLoader 中的本类无法互转。
     * <p>
     * 兜底：`getContext` 按 Class 身份取值
     * </p>
     * 即使加载器仍不一致，也不直接强转：
     * <li>
     * <ul>
     * 1. `value.getClass() == WorkflowContext.class`（同一个 Class 对象）才强转
     * </ul>
     * <ul>
     * 2. 否则 JSON 序列化再 `toBean` 成 **当前代码所在加载器** 的实例
     * </ul>
     * </li>
     * JSON 只认字段，不认 ClassLoader，用来跨加载器搬数据。
     */
    public static WorkflowContext getContext(MessagesState<String> state) {
        //   return (WorkflowContext) state.data().get(WORKFLOW_CONTEXT_KEY); 出问题的代码
        Object value = state.data().get(WORKFLOW_CONTEXT_KEY);
        if (value == null) {
            return null;
        }
        if (value.getClass() == WorkflowContext.class) {
            return (WorkflowContext) value;
        }
        log.warn("WorkflowContext ClassLoader 不一致，source={}, target={}，已按 JSON 转换",
                value.getClass().getClassLoader(), WorkflowContext.class.getClassLoader());
        return JSONUtil.toBean(JSONUtil.toJsonStr(value), WorkflowContext.class);
    }

    /**
     * 将 WorkflowContext 保存到 MessagesState 中
     */
    public static Map<String, Object> saveContext(WorkflowContext context) {
        return Map.of(WORKFLOW_CONTEXT_KEY, context);
    }
}

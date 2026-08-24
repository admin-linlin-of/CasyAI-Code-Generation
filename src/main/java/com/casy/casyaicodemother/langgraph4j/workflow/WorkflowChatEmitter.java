package com.casy.casyaicodemother.langgraph4j.workflow;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.casy.casyaicodemother.ai.model.ImageCollectionPlan;
import com.casy.casyaicodemother.langgraph4j.model.QualityResult;
import com.casy.casyaicodemother.langgraph4j.state.ImageResource;
import com.casy.casyaicodemother.langgraph4j.state.WorkflowContext;
import reactor.core.publisher.FluxSink;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

/**
 * 工作流对话推送器。
 * <p>
 * 聊天接口走 {@code Flux<String>} SSE：每个节点跑完后要把进度立刻推到前端，
 * 而 langgraph 节点是静态方法，拿不到 FluxSink。这里用线程安全的静态引用把 sink 挂上，
 * 节点和主循环都能 {@link #emit(String)} / {@link #emitChunked(String)}。
 * </p>
 * <p>
 * 一次性 {@code sink.next(整段文字)} 时，Tomcat/Spring MVC 容易把小包攒到工作流结束才刷出，
 * 左侧对话就会「整段蹦出来」。{@link #emitChunked(String)} 按 4 字切包并 park 12ms，
 * 逼 SSE 刷出去，前端打字机才跟得上。
 * </p>
 */
public final class WorkflowChatEmitter {

    /** 当前这一轮工作流绑定的 sink；并发两轮时后一次会覆盖，业务上同一用户同时只跑一轮 */
    private static final AtomicReference<FluxSink<String>> SINK = new AtomicReference<>();

    private WorkflowChatEmitter() {
    }

    /**
     * 工作流虚拟线程开始时绑定 sink，之后节点里就能推文本。
     */
    public static void bind(FluxSink<String> sink) {
        SINK.set(sink);
    }

    /**
     * 工作流结束（成功/失败/取消）必须解开，避免旧 sink 被下一轮误用。
     */
    public static void unbind() {
        SINK.set(null);
    }

    /**
     * 立刻推一小段（心跳点、短提示）。不切包、不延迟。
     */
    public static void emit(String text) {
        FluxSink<String> sink = SINK.get();
        if (sink == null || sink.isCancelled() || StrUtil.isBlank(text)) {
            return;
        }
        sink.next(text);
    }

    public static void emitPing() {
        FluxSink<String> sink = SINK.get();
        if (sink == null || sink.isCancelled()) {
            return;
        }
        sink.next("{\"t\":\"ping\"}");
    }

    /**
     * 把长 Markdown 切成约 4 字一块推出去，块与块之间停 12ms。
     * 前端 AiMarkdownMessage 再按帧打字，合起来就是打字机效果。
     */
    public static void emitChunked(String text) {
        FluxSink<String> sink = SINK.get();
        if (sink == null || sink.isCancelled() || StrUtil.isBlank(text)) {
            return;
        }
        int i = 0;
        while (i < text.length() && !sink.isCancelled()) {
            int end = Math.min(i + 4, text.length());
            sink.next(text.substring(i, end));
            i = end;
            // 12ms：既够 TCP/SSE 刷缓冲，又不会把总耗时拉得太长
            LockSupport.parkNanos(12_000_000L);
        }
    }

    /**
     * 按节点 {@code currentStep} 拼一段给用户看的 Markdown（模型、素材图、质检结论等）。
     * case 字符串必须和各 Node 里 {@code setCurrentStep(...)} 完全一致。
     */
    public static String formatStep(int stepNo, WorkflowContext ctx) {
        String step = StrUtil.blankToDefault(ctx.getCurrentStep(), "处理中");
        StringBuilder sb = new StringBuilder();
        sb.append("\n**步骤 ").append(stepNo).append(" · ").append(step).append("**\n");
        switch (step) {
            case "创建应用", "加载应用" -> sb.append("- 应用 ID：").append(ctx.getAppId()).append('\n');
            case "识别生成模式" -> {
                sb.append("- 模式：").append(Boolean.TRUE.equals(ctx.getEditMode()) ? "修改已有网站" : "首次生成").append('\n');
                if (Boolean.TRUE.equals(ctx.getNeedNewImages())) {
                    sb.append("- 本轮需要补充图片素材\n");
                }
                if (StrUtil.isNotBlank(ctx.getExistingCodeDir())) {
                    sb.append("- 已有代码：`").append(ctx.getExistingCodeDir()).append("`\n");
                }
            }
            case "图片计划" -> appendPlan(sb, ctx.getImageCollectionPlan());
            case "内容图片收集" -> appendImages(sb, ctx.getContentImages());
            case "插画图片收集" -> appendImages(sb, ctx.getIllustrations());
            case "架构图生成" -> appendImages(sb, ctx.getDiagrams());
            case "Logo生成" -> appendImages(sb, ctx.getLogos());
            case "图片聚合" -> {
                sb.append("- 素材合计：").append(CollUtil.size(ctx.getImageList())).append(" 张\n");
                appendImages(sb, ctx.getImageList());
            }
            case "提示词增强" -> sb.append("- 增强后提示词长度：")
                    .append(StrUtil.length(ctx.getEnhancedPrompt())).append(" 字\n");
            case "智能选择代码生成类型" -> {
                if (ctx.getGenerationType() != null) {
                    sb.append("- 生成类型：").append(ctx.getGenerationType().getText())
                            .append(" (`").append(ctx.getGenerationType().getValue()).append("`)\n");
                }
            }
            case "智能选择代码生成模型" -> {
                if (ctx.getModelTypeEnum() != null) {
                    sb.append("- 选用模型：").append(ctx.getModelTypeEnum().getModelName()).append('\n');
                }
            }
            case "保存用户消息" -> sb.append("- 用户消息已写入对话历史\n");
            case "代码生成" -> {
                if (ctx.getGenerationType() != null) {
                    sb.append("- 类型：").append(ctx.getGenerationType().getText()).append('\n');
                }
                if (ctx.getModelTypeEnum() != null) {
                    sb.append("- 模型：").append(ctx.getModelTypeEnum().getModelName()).append('\n');
                }
                if (StrUtil.isNotBlank(ctx.getGeneratedCodeDir())) {
                    sb.append("- 输出目录：`").append(ctx.getGeneratedCodeDir()).append("`\n");
                }
            }
            case "代码质量检查" -> appendQuality(sb, ctx.getQualityResult());
            case "代码修复" -> sb.append("- 修复轮次：")
                    .append(ctx.getRepairCount() == null ? 1 : ctx.getRepairCount())
                    .append('/').append(WorkflowContext.MAX_REPAIR_COUNT).append('\n');
            case "项目构建" -> {
                if (StrUtil.isNotBlank(ctx.getBuildResultDir())) {
                    sb.append("- 构建目录：`").append(ctx.getBuildResultDir()).append("`\n");
                }
            }
            case "网站预览图" -> sb.append("- 已提交后台截图，不阻塞主流程\n");
            default -> {
            }
        }
        if (StrUtil.isNotBlank(ctx.getErrorMessage())) {
            sb.append("- 备注：").append(ctx.getErrorMessage()).append('\n');
        }
        return sb.toString();
    }

    /** 图片计划：各类任务数量 + 前几条 query，方便用户看到「正在搜什么」 */
    private static void appendPlan(StringBuilder sb, ImageCollectionPlan plan) {
        if (plan == null) {
            sb.append("- 未生成图片计划\n");
            return;
        }
        sb.append("- 内容图任务：").append(CollUtil.size(plan.getContentImageTasks())).append('\n');
        sb.append("- 插画任务：").append(CollUtil.size(plan.getIllustrationTasks())).append('\n');
        sb.append("- 架构图任务：").append(CollUtil.size(plan.getDiagramTasks())).append('\n');
        sb.append("- Logo 任务：").append(CollUtil.size(plan.getLogoTasks())).append('\n');
        appendTaskQueries(sb, "内容图", plan.getContentImageTasks() == null ? null
                : plan.getContentImageTasks().stream().map(ImageCollectionPlan.ImageSearchTask::query).toList());
        appendTaskQueries(sb, "插画", plan.getIllustrationTasks() == null ? null
                : plan.getIllustrationTasks().stream().map(ImageCollectionPlan.IllustrationTask::query).toList());
        appendTaskQueries(sb, "Logo", plan.getLogoTasks() == null ? null
                : plan.getLogoTasks().stream().map(ImageCollectionPlan.LogoTask::description).toList());
    }

    /** 每类任务最多列出 3 条，避免对话被 query 刷屏 */
    private static void appendTaskQueries(StringBuilder sb, String title, List<String> queries) {
        if (CollUtil.isEmpty(queries)) {
            return;
        }
        int n = 0;
        for (String q : queries) {
            if (StrUtil.isBlank(q) || ++n > 3) {
                continue;
            }
            sb.append("  - ").append(title).append("：").append(q).append('\n');
        }
    }

    /**
     * 把收集到的图写成 Markdown 图片。前端 markdown-it 会渲染成 {@code <img>}。
     * 每步最多 4 张，防止对话过长。
     */
    private static void appendImages(StringBuilder sb, List<ImageResource> images) {
        if (CollUtil.isEmpty(images)) {
            sb.append("- 本步未收集到图片\n");
            return;
        }
        sb.append("- 本步图片：").append(images.size()).append(" 张\n");
        int n = 0;
        for (ImageResource image : images) {
            if (image == null || StrUtil.isBlank(image.getUrl()) || ++n > 4) {
                continue;
            }
            String desc = StrUtil.blankToDefault(image.getDescription(), "素材");
            sb.append("![").append(desc).append("](").append(image.getUrl()).append(")\n");
            sb.append(desc);
            if (image.getCategory() != null) {
                sb.append(" · ").append(image.getCategory().getText());
            }
            sb.append('\n');
        }
    }

    /** 质检通过/失败 + 问题和建议各最多 5 条 */
    private static void appendQuality(StringBuilder sb, QualityResult result) {
        if (result == null) {
            sb.append("- 无质检结果\n");
            return;
        }
        sb.append("- 结果：").append(Boolean.TRUE.equals(result.getIsValid()) ? "通过" : "未通过").append('\n');
        if (CollUtil.isNotEmpty(result.getErrors())) {
            result.getErrors().stream().limit(5).forEach(err -> sb.append("  - 问题：").append(err).append('\n'));
        }
        if (CollUtil.isNotEmpty(result.getSuggestions())) {
            result.getSuggestions().stream().limit(5).forEach(s -> sb.append("  - 建议：").append(s).append('\n'));
        }
    }
}

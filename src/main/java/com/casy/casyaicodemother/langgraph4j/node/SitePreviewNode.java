package com.casy.casyaicodemother.langgraph4j.node;

import cn.hutool.core.util.StrUtil;
import com.casy.casyaicodemother.langgraph4j.state.WorkflowContext;
import com.casy.casyaicodemother.model.enums.CodeGenTypeEnum;
import com.casy.casyaicodemother.service.AppService;
import com.casy.casyaicodemother.util.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.springframework.core.env.Environment;

import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 网站预览图节点：不在图里同步截图，只保证任务已提交，然后立刻结束。
 * <p>
 * Chrome 截图很慢，放进主链路会堵住质检/构建。真正的截图在
 * {@link AppService#generateAppCoverAsync} 虚拟线程里跑。
 * HTML/多文件在代码生成节点就能 submit；Vue 要等 dist 打出来，在项目构建成功后再 submit。
 * 本节点用 ConcurrentHashMap#computeIfAbsent 兜底：前面没提交过就补一次，提交过则复用同一个 Future。
 * </p>
 */
@Slf4j
public class SitePreviewNode {

    /**
     * appId → 截图任务。computeIfAbsent 保证同一应用本轮只截一次。
     * awaitCover 会 remove，避免泄漏。
     */
    private static final ConcurrentHashMap<Long, CompletableFuture<String>> JOBS = new ConcurrentHashMap<>();

    /**
     * 图上的站点预览节点：提交（或复用）截图任务后马上返回，currentStep 供对话展示。
     */
    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            submit(context.getAppId(), context.getGeneratedCodeDir(), context.getGenerationType());
            context.setCurrentStep("网站预览图");
            return WorkflowContext.saveContext(context);
        });
    }

    /**
     * 异步提交截图。同一 appId 已有任务则忽略。
     * HTML 在代码写盘后即可调；Vue 必须在 npm build 成功、dist 存在后再调。
     */
    public static void submit(Long appId, String generatedCodeDir, CodeGenTypeEnum generationType) {
        if (appId == null || appId <= 0 || StrUtil.isBlank(generatedCodeDir)) {
            return;
        }
        String previewUrl = buildPreviewUrl(generatedCodeDir, generationType);
        if (StrUtil.isBlank(previewUrl)) {
            return;
        }
        JOBS.computeIfAbsent(appId, id -> {
            log.info("提交网站预览截图任务 appId={}, url={}", id, previewUrl);
            AppService appService = SpringContextUtil.getBean(AppService.class);
            return appService.generateAppCoverAsync(id, previewUrl);
        });
    }

    /**
     * 工作流主循环结束时等截图结果，好把封面 URL 写进本轮对话。
     * 超时返回 null，对话里提示「仍在后台生成」，封面稍后仍会更新。
     */
    public static String awaitCover(Long appId, long timeoutSeconds) {
        if (appId == null) {
            return null;
        }
        CompletableFuture<String> job = JOBS.remove(appId);
        if (job == null) {
            return null;
        }
        try {
            return job.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("等待网站预览图超时或失败 appId={}: {}", appId, e.getMessage());
            return null;
        }
    }

    /**
     * 拼本机静态预览地址，给无头 Chrome 打开。
     * 目录名即 StaticResourceController 的 deployKey，例如 {@code html_123_v1}。
     * Vue 预览页在 {@code dist/index.html}。
     */
    private static String buildPreviewUrl(String generatedCodeDir, CodeGenTypeEnum generationType) {
        File dir = new File(generatedCodeDir);
        String key = dir.getName();
        if (StrUtil.isBlank(key)) {
            return null;
        }
        Environment env = SpringContextUtil.getBean(Environment.class);
        String port = env.getProperty("server.port", "8124");
        String ctx = env.getProperty("server.servlet.context-path", "");
        String base = "http://127.0.0.1:" + port + ctx + "/static/" + key + "/";
        if (generationType == CodeGenTypeEnum.VUE_PROJECT) {
            return base + "dist/index.html";
        }
        return base;
    }
}

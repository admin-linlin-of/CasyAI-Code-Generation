package com.casy.casyaicodemother.core;

import com.casy.casyaicodemother.model.enums.CodeGenTypeEnum;
import com.casy.casyaicodemother.model.enums.ModelTypeEnum;
import com.casy.casyaicodemother.service.AppVersionService;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 代码生成上下文持有者（VUE_PROJECT 专用）。
 * <p>
 * 背景：AI 通过 {@link com.casy.casyaicodemother.ai.tools.FileWriteTool} 写文件时，
 * 工具方法只能拿到 appId（@ToolMemoryId），拿不到 modelType 和 userMessageId。
 * 但创建版本记录 {@link AppVersionService#createCodeVersion} 需要这三个参数。
 * <p>
 * 因此用 ConcurrentHashMap 以 appId 为 key，在流式生成开始前存入上下文，
 * 首次写文件时懒创建版本，流结束后清理。
 * <p>
 * 整体流程（多版本 + 共用 node_modules）：
 * <pre>
 * 1. AiCodeGeneratorFacade.set(appId, modelType, userMessageId)
 * 2. AI 首次 writeFile → createCodeVersion
 *      ├─ v1：空目录
 *      └─ v2+：VueProjectVersionManager 从上一版复制（不含 node_modules/dist）
 * 3. AI 写入/覆盖文件（增量修改时可能只写一个 .vue）
 * 4. 前端调用 /tAppVersion/build 触发 VueProjectBuilder 异步 build
 *      ├─ npm install 仅在 vue_project_{appId}_shared
 *      └─ 版本目录 node_modules 链接到 shared
 * 5. AiCodeGeneratorFacade.remove(appId)
 * </pre>
 */
public class CodeGenContextHolder {

    /** key = appId，value = 本次生成会话的上下文（同一 app 同时只应有一个生成流） */
    private static final ConcurrentHashMap<Long, CodeGenContext> CONTEXT_MAP = new ConcurrentHashMap<>();

    /**
     * 在 VUE_PROJECT 流式生成开始前调用，保存 modelType 和 userMessageId 供后续创建版本使用。
     */
    public static void set(Long appId, ModelTypeEnum modelType, Long userMessageId, String versionDir) {
        CONTEXT_MAP.put(appId, new CodeGenContext(modelType, userMessageId, versionDir));
    }

    /**
     * 流式生成结束（成功/失败/取消）后调用，防止内存泄漏和上下文污染。
     */
    public static void remove(Long appId) {
        CONTEXT_MAP.remove(appId);
    }

    /**
     * 获取版本目录名（如 v1、v2），若尚未创建则首次调用时创建。
     * <p>
     * 双重检查锁保证：AI 并发写多个文件时，只插入一条版本记录、只分配一个版本号。
     *
     * @return 版本目录名；若未 set 过上下文则返回 null（FileWriteTool 会降级到旧目录格式）
     */
    public static String getOrCreateVersionDir(Long appId, AppVersionService appVersionService) {
        CodeGenContext ctx = CONTEXT_MAP.get(appId);
        if (ctx == null) {
            return null;
        }
        if (ctx.versionDir == null) {
            synchronized (ctx) {
                if (ctx.versionDir == null) {
                    // 写入 t_app_version 表，并返回 code_dir 字段（如 v3）
                    ctx.versionDir = appVersionService.createCodeVersion(appId, ctx.modelType, ctx.userMessageId);
                }
            }
        }
        return ctx.versionDir;
    }

    /**
     * 只读获取已创建的版本目录，不触发新建（供流结束后的 npm build 使用）。
     */
    public static String getVersionDir(Long appId) {
        CodeGenContext ctx = CONTEXT_MAP.get(appId);
        return ctx == null ? null : ctx.versionDir;
    }

    /**
     * 拼接磁盘上的项目根目录名，与 {@link com.casy.casyaicodemother.core.save.CodeFileSaverTemplate} 保持一致。
     * 示例：vue_project_421846728331051008_v1
     */
    public static String buildProjectDirName(Long appId, String versionDir) {
        return String.format("%s_%s_%s", CodeGenTypeEnum.VUE_PROJECT.getValue(), appId, versionDir);
    }

    /** 单次 VUE 生成会话的上下文数据 */
    private static class CodeGenContext {
        /** 本次使用的 AI 模型，写入版本表的 model_type 字段 */
        private final ModelTypeEnum modelType;
        /** 触发本次生成的用户消息 ID，写入版本表的 chat_history_id 字段 */
        private final Long userMessageId;
        /** 懒创建后的版本目录名，如 v1 */
        private volatile String versionDir;

        private CodeGenContext(ModelTypeEnum modelType, Long userMessageId, String versionDir) {
            this.modelType = modelType;
            this.userMessageId = userMessageId;
            this.versionDir = versionDir;
        }
    }
}

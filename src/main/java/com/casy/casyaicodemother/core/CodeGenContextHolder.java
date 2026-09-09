package com.casy.casyaicodemother.core;

import cn.hutool.core.util.StrUtil;
import com.casy.casyaicodemother.constant.AppConstant;
import com.casy.casyaicodemother.model.enums.CodeGenTypeEnum;
import com.casy.casyaicodemother.model.enums.ModelTypeEnum;
import com.casy.casyaicodemother.service.AppVersionService;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 代码生成上下文持有者（VUE_PROJECT 专用）。
 * <p>
 * 背景：AI 通过文件工具读写时只能拿到 appId（@ToolMemoryId），拿不到 modelType 和 userMessageId。
 * 但创建版本记录 {@link AppVersionService#createCodeVersion} 需要这三个参数。
 * <p>
 * 因此用 ConcurrentHashMap 以 appId 为 key，在流式生成开始前存入上下文并创建本轮版本，
 * 所有文件工具都打到该版本目录，流结束后清理。
 * <p>
 * 整体流程（多版本 + 共用 node_modules）：
 * <pre>
 * 1. generateAndSaveCodeStream：createCodeVersion + set(appId, …, 新 versionDir)
 *      ├─ v1：空目录
 *      └─ v2+：从上一版复制源码（不含 node_modules/dist）
 * 2. AI readFile / modifyFile / writeFile 都写入该新目录（不再等首次 writeFile）
 * 3. 前端调用 /tAppVersion/build 触发 VueProjectBuilder 异步 build
 * 4. remove(appId)
 * </pre>
 */
@Slf4j
public class CodeGenContextHolder {

    /** key = appId，value = 本次生成会话的上下文（同一 app 同时只应有一个生成流） */
    private static final ConcurrentHashMap<Long, CodeGenContext> CONTEXT_MAP = new ConcurrentHashMap<>();

    /**
     * 在 VUE_PROJECT 流式生成开始前调用，保存 modelType 和 userMessageId 供后续创建版本使用。
     */
    public static void set(Long appId, ModelTypeEnum modelType, Long userMessageId, String versionDir) {
        set(appId, modelType, userMessageId, versionDir, CodeGenTypeEnum.VUE_PROJECT);
    }

    /**
     * 生成或修复开始前写入上下文；修复时必须传入已有 versionDir，避免新建版本。
     */
    public static void set(Long appId, ModelTypeEnum modelType, Long userMessageId, String versionDir,
                           CodeGenTypeEnum codeGenType) {
        CONTEXT_MAP.put(appId, new CodeGenContext(modelType, userMessageId, versionDir,
                codeGenType == null ? CodeGenTypeEnum.VUE_PROJECT : codeGenType));
    }

    /**
     * 流式生成结束（成功/失败/取消）后调用，防止内存泄漏和上下文污染。
     */
    public static void remove(Long appId) {
        CONTEXT_MAP.remove(appId);
    }

    /**
     * 获取本轮版本目录名（如 v1、v2）。生成入口应已创建；若仍为空则在此补建（修改工具兜底）。
     * <p>
     * 双重检查锁保证：并发读写时只插入一条版本记录、只分配一个版本号。
     *
     * @return 版本目录名；若未 set 过上下文则返回 null
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
        CodeGenContext ctx = CONTEXT_MAP.get(appId);
        String type = (ctx != null && ctx.codeGenType != null)
                ? ctx.codeGenType.getValue()
                : CodeGenTypeEnum.VUE_PROJECT.getValue();
        return String.format("%s_%s_%s", type, appId, versionDir);
    }

    /**
     * 当前会话对应的磁盘项目目录名，读写工具共用，禁止再拼 vue_project_{id}/{v1}。
     */
    public static String getProjectDirName(Long appId) {
        String versionDir = getVersionDir(appId);
        if (versionDir == null) {
            CodeGenContext ctx = CONTEXT_MAP.get(appId);
            String type = (ctx != null && ctx.codeGenType != null)
                    ? ctx.codeGenType.getValue()
                    : CodeGenTypeEnum.VUE_PROJECT.getValue();
            return type + "_" + appId;
        }
        return buildProjectDirName(appId, versionDir);
    }

    /**
     * 把工具传入的相对（或误传的绝对）路径解析到本轮版本目录内。
     * <p>
     * 修改模式提示词里曾经带过上一版绝对路径，模型可能把 {@code .../vue_project_{id}_v1/src/App.vue}
     * 直接当路径写入，这里会重定向到当前会话目录，避免改回 v1。
     */
    public static Path resolveProjectPath(Long appId, String relativeFilePath) {
        Path projectRoot = Paths.get(AppConstant.CODE_OUTPUT_ROOT_DIR, getProjectDirName(appId))
                .toAbsolutePath().normalize();
        if (StrUtil.isBlank(relativeFilePath)) {
            return projectRoot;
        }
        Path input = Paths.get(relativeFilePath);
        Path resolved;
        if (input.isAbsolute()) {
            Path normalized = input.toAbsolutePath().normalize();
            if (normalized.startsWith(projectRoot)) {
                resolved = normalized;
            } else {
                Path remapped = remapToCurrentProject(appId, normalized, projectRoot);
                if (remapped == null) {
                    throw new IllegalArgumentException(
                            "请使用项目内相对路径（例如 src/App.vue），不要使用绝对磁盘路径: " + relativeFilePath);
                }
                log.warn("绝对路径已重定向到当前版本目录: {} -> {}", normalized, remapped);
                resolved = remapped;
            }
        } else {
            resolved = projectRoot.resolve(relativeFilePath).normalize();
        }
        if (!resolved.startsWith(projectRoot)) {
            throw new IllegalArgumentException("路径越出项目目录: " + relativeFilePath);
        }
        return resolved;
    }

    /**
     * 写/改/删文件前确保本轮版本已创建，再解析路径。
     */
    public static Path resolveProjectPathForWrite(Long appId, String relativeFilePath,
                                                  AppVersionService appVersionService) {
        getOrCreateVersionDir(appId, appVersionService);
        return resolveProjectPath(appId, relativeFilePath);
    }

    private static Path remapToCurrentProject(Long appId, Path absolute, Path projectRoot) {
        Path outputRoot = Paths.get(AppConstant.CODE_OUTPUT_ROOT_DIR).toAbsolutePath().normalize();
        if (!absolute.startsWith(outputRoot)) {
            return null;
        }
        Path relativeToOutput = outputRoot.relativize(absolute);
        if (relativeToOutput.getNameCount() < 1) {
            return null;
        }
        String first = relativeToOutput.getName(0).toString();
        if (!belongsToApp(appId, first)) {
            return null;
        }
        Path rest = relativeToOutput.getNameCount() == 1
                ? Path.of("")
                : relativeToOutput.subpath(1, relativeToOutput.getNameCount());
        return projectRoot.resolve(rest).normalize();
    }

    private static boolean belongsToApp(Long appId, String dirName) {
        CodeGenContext ctx = CONTEXT_MAP.get(appId);
        String type = (ctx != null && ctx.codeGenType != null)
                ? ctx.codeGenType.getValue()
                : CodeGenTypeEnum.VUE_PROJECT.getValue();
        String prefix = type + "_" + appId;
        return dirName.equals(prefix) || dirName.startsWith(prefix + "_");
    }

    /** 单次 VUE 生成会话的上下文数据 */
    private static class CodeGenContext {
        /** 本次使用的 AI 模型，写入版本表的 model_type 字段 */
        private final ModelTypeEnum modelType;
        /** 触发本次生成的用户消息 ID，写入版本表的 chat_history_id 字段 */
        private final Long userMessageId;
        /** 懒创建后的版本目录名，如 v1 */
        private volatile String versionDir;
        /** 代码生成类型，决定磁盘目录前缀 */
        private final CodeGenTypeEnum codeGenType;

        private CodeGenContext(ModelTypeEnum modelType, Long userMessageId, String versionDir,
                               CodeGenTypeEnum codeGenType) {
            this.modelType = modelType;
            this.userMessageId = userMessageId;
            this.versionDir = versionDir;
            this.codeGenType = codeGenType;
        }
    }
}

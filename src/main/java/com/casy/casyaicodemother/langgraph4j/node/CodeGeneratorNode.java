package com.casy.casyaicodemother.langgraph4j.node;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.casy.casyaicodemother.constant.AppConstant;
import com.casy.casyaicodemother.core.AiCodeGeneratorFacade;
import com.casy.casyaicodemother.core.vue.VueProjectVersionManager;
import com.casy.casyaicodemother.exception.ErrorCode;
import com.casy.casyaicodemother.exception.ThrowUtils;
import com.casy.casyaicodemother.langgraph4j.state.WorkflowContext;
import com.casy.casyaicodemother.model.enums.CodeGenTypeEnum;
import com.casy.casyaicodemother.model.enums.ModelTypeEnum;
import com.casy.casyaicodemother.service.AppVersionService;
import com.casy.casyaicodemother.langgraph4j.workflow.WorkflowChatEmitter;
import com.casy.casyaicodemother.util.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Map;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 代码生成节点
 */
@Slf4j
public class CodeGeneratorNode {

    /**
     * 工具名 → 前端工具徽标标签名（markdownRenderer 白名单：fileWrite/fileModify/fileRead/fileDelete/dirRead）。
     * 工作流对话输出与 AiMarkdownMessage 相同的 <标签> 语法，复用同一套带图标的徽标样式。
     */
    private static final Map<String, String> TOOL_TAG_NAMES = Map.of(
            "writeFile", "fileWrite",
            "modifyFile", "fileModify",
            "readFile", "fileRead",
            "deleteFile", "fileDelete",
            "readDir", "dirRead"
    );

    /** 工具名 → 展示用中文动作（失败行与兜底展示） */
    private static final Map<String, String> TOOL_LABELS = Map.of(
            "writeFile", "写入文件",
            "modifyFile", "修改文件",
            "deleteFile", "删除文件",
            "readFile", "读取文件",
            "readDir", "浏览目录"
    );

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            log.info("执行节点: 代码生成");

            // 构造用户消息
            String userMessage = context.getEnhancedPrompt();

            CodeGenTypeEnum generationType = context.getGenerationType();
            ModelTypeEnum generationModel = context.getModelTypeEnum();
            Long appId = context.getAppId();
            // 必须使用真实 appId：Redis 记忆、createCodeVersion 查 t_app、落盘目录都绑定它。
            // appId=0 会导致脏记忆、空响应，以及「应用不存在」后文件根本不会写出。
            ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "工作流缺少真实 appId");
            Long userMessageId = context.getUserMessageId();

            AiCodeGeneratorFacade codeGeneratorFacade = SpringContextUtil.getBean(AiCodeGeneratorFacade.class);
            log.info("开始{}代码，类型: {} ({})，appId: {}",
                    Boolean.TRUE.equals(context.getEditMode()) ? "修改" : "生成",
                    generationType.getValue(), generationType.getText(), appId);

            // 每轮对话新建版本：Vue 在 generateAndSaveCodeStream 开头 createCodeVersion（v2+ 复制上一版）；
            // HTML/MULTI_FILE 在流结束落盘时创建。修复节点走 repairCodeStream，不会进这里。
            Flux<String> codeStream = codeGeneratorFacade.generateAndSaveCodeStream(
                    userMessage, generationType, generationModel, appId, userMessageId, null);
            // 代码流本身不进对话（避免把 HTML 源码刷到左侧）。先提示「正在生成」，
            // 再把 Vue 工程逐条工具执行结果翻译成进度推给前端；非 Vue 把源码走 t=code 推到右侧代码区。
            WorkflowChatEmitter.emitChunked(appId, Boolean.TRUE.equals(context.getEditMode())
                    ? "\n正在按你的要求修改已有网站…\n"
                    : "\n代码生成中，模型正在输出…\n");
            boolean isVueWorkflow = generationType == CodeGenTypeEnum.VUE_PROJECT;
            java.util.concurrent.atomic.AtomicLong lastBeat = new java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis());
            codeStream
                    .doOnNext(chunk -> {
                        long now = System.currentTimeMillis();
                        if (isVueWorkflow) {
                            forwardToolExecuted(appId, chunk);
                        } else if (StrUtil.isNotBlank(chunk) && !chunk.contains("\"t\":\"ping\"")) {
                            // HTML / 多文件：源码经 t=code 进右侧面板，不污染工作流步骤卡片
                            WorkflowChatEmitter.emit(appId, cn.hutool.json.JSONUtil.toJsonStr(
                                    java.util.Map.of("t", "code", "c", chunk)));
                        }
                        if (now - lastBeat.get() >= 1600) {
                            lastBeat.set(now);
                            WorkflowChatEmitter.emitPing(appId);
                        }
                    })
                    .blockLast(Duration.ofMinutes(10));

            AppVersionService appVersionService = SpringContextUtil.getBean(AppVersionService.class);
            String versionDir = appVersionService.getLatestCodeDir(appId);
            String generatedCodeDir = resolveGeneratedCodeDir(generationType, appId, versionDir);
            log.info("AI 代码生成完成，生成目录: {}", generatedCodeDir);

            context.setCurrentStep("代码生成");
            context.setGeneratedCodeDir(generatedCodeDir);
            // HTML / 多文件写盘后静态预览已可访问，立刻后台截封面，与后续质检并行。
            // Vue 要等 npm build 出 dist，改在 ProjectBuilderNode 成功后再 submit。
            if (generationType != CodeGenTypeEnum.VUE_PROJECT) {
                SitePreviewNode.submit(appId, generatedCodeDir, generationType);
            }
            return WorkflowContext.saveContext(context);
        });
    }

    /**
     * 把 Vue TokenStream 的 tool_executed JSON 事件转成一行可读进度推给工作流对话。
     * <p>
     * 只转发“动作+文件路径”，不转发 readFile/readDir 的返回正文与 writeFile 的参数内容，
     * 避免把整份源码/大目录树刷到左侧对话；失败时带上前 160 字原因帮助定位。
     */
    private static void forwardToolExecuted(Long appId, String chunk) {
        if (appId == null || StrUtil.isBlank(chunk)) {
            return;
        }
        JSONObject msg;
        try {
            msg = JSONUtil.parseObj(chunk);
        } catch (Exception ignored) {
            return;
        }
        if (!"tool_executed".equals(msg.getStr("type"))) {
            return;
        }
        String name = StrUtil.blankToDefault(msg.getStr("name"), "");
        String label = TOOL_LABELS.getOrDefault(name, name);
        String path = extractToolPath(msg.getStr("arguments"));
        boolean failed = Boolean.TRUE.equals(msg.getBool("failed"));
        if (!failed) {
            // 成功：复用与 AiMarkdownMessage 相同的工具徽标标签，前端渲染成带图标的徽标
            String tag = TOOL_TAG_NAMES.getOrDefault(name, "toolCall");
            String content = path != null ? "`" + path + "`" : "完成";
            WorkflowChatEmitter.emitChunked(appId, "- <" + tag + ">" + content + "</" + tag + ">\n");
            // 写入/修改成功时同步推 t=file，右侧代码区实时打字机展示
            emitWorkflowFileEvent(appId, name, msg.getStr("arguments"), path);
            return;
        }
        // 失败：普通行更醒目（徽标没有失败态）
        StringBuilder line = new StringBuilder("- ⚠️ ").append(label);
        if (path != null) {
            line.append(" `").append(path).append('`');
        }
        String reason = StrUtil.blankToDefault(msg.getStr("result"), "未知错误");
        line.append(" 失败：").append(truncate(reason, 160)).append('\n');
        WorkflowChatEmitter.emitChunked(appId, line.toString());
    }

    /** 从 writeFile 等 arguments JSON 中提取相对路径（relativeFilePath / relativeDirPath） */
    private static String extractToolPath(String arguments) {
        if (StrUtil.isBlank(arguments)) {
            return null;
        }
        try {
            JSONObject args = JSONUtil.parseObj(arguments);
            String path = StrUtil.blankToDefault(args.getStr("relativeFilePath"),
                    args.getStr("relativeDirPath"));
            return StrUtil.isBlank(path) ? null : path;
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 工作流 Vue：把 writeFile / modifyFile 的结果推成 t=file，供右侧代码区实时展示。
     * 不走聊天正文，避免步骤卡片被源码污染。
     */
    private static void emitWorkflowFileEvent(Long appId, String toolName, String arguments, String path) {
        if (appId == null || StrUtil.isBlank(path)) {
            return;
        }
        try {
            if ("writeFile".equals(toolName)) {
                JSONObject args = StrUtil.isBlank(arguments) ? new JSONObject() : JSONUtil.parseObj(arguments);
                String content = args.getStr("content", "");
                boolean append = Boolean.TRUE.equals(args.getBool("append"));
                java.util.Map<String, Object> fileEvent = new java.util.LinkedHashMap<>();
                fileEvent.put("t", "file");
                fileEvent.put("path", path);
                fileEvent.put("content", content);
                fileEvent.put("append", append);
                fileEvent.put("done", !append);
                WorkflowChatEmitter.emit(appId, JSONUtil.toJsonStr(fileEvent));
                return;
            }
            if ("modifyFile".equals(toolName)) {
                java.nio.file.Path disk = com.casy.casyaicodemother.core.CodeGenContextHolder.resolveProjectPath(appId, path);
                if (disk == null || !java.nio.file.Files.isRegularFile(disk)) {
                    return;
                }
                String diskContent = java.nio.file.Files.readString(disk);
                java.util.Map<String, Object> fileEvent = new java.util.LinkedHashMap<>();
                fileEvent.put("t", "file");
                fileEvent.put("path", path);
                fileEvent.put("content", diskContent);
                fileEvent.put("append", false);
                fileEvent.put("done", true);
                WorkflowChatEmitter.emit(appId, JSONUtil.toJsonStr(fileEvent));
            }
        } catch (Exception e) {
            log.warn("工作流推送文件事件失败 appId={} path={}", appId, path, e);
        }
    }

    private static String truncate(String text, int max) {
        if (text == null || text.length() <= max) {
            return text;
        }
        return text.substring(0, max) + "…";
    }

    /**
     * 按真实版本目录拼落盘路径；尚未写出文件时返回空，交给质检判定失败。
     */
    private static String resolveGeneratedCodeDir(CodeGenTypeEnum generationType, Long appId, String versionDir) {
        if (StrUtil.isBlank(versionDir)) {
            log.warn("代码版本未创建，文件可能未写出，appId={}", appId);
            return "";
        }
        if (generationType == CodeGenTypeEnum.VUE_PROJECT) {
            VueProjectVersionManager versionManager = SpringContextUtil.getBean(VueProjectVersionManager.class);
            return versionManager.getVersionDir(appId, versionDir).getAbsolutePath();
        }
        return String.format("%s/%s_%s_%s",
                AppConstant.CODE_OUTPUT_ROOT_DIR, generationType.getValue(), appId, versionDir);
    }

}

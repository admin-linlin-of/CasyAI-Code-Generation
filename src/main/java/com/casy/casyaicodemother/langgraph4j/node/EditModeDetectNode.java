package com.casy.casyaicodemother.langgraph4j.node;

import cn.hutool.core.util.StrUtil;
import com.casy.casyaicodemother.constant.AppConstant;
import com.casy.casyaicodemother.core.vue.VueProjectVersionManager;
import com.casy.casyaicodemother.langgraph4j.state.WorkflowContext;
import com.casy.casyaicodemother.model.enums.CodeGenTypeEnum;
import com.casy.casyaicodemother.service.AppVersionService;
import com.casy.casyaicodemother.util.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 识别本轮是「从零生成」还是「改已有网站」。
 * <p>
 * 只看磁盘：最新版本目录里有没有源码。有 → {@code editMode=true}，后面默认跳过四路搜图。
 * 用户明确要换图时 {@code needNewImages=true}，修改模式仍走图片收集。
 * 这里不改 {@code versionDir}，新版本仍由代码生成节点按原规则创建，避免覆盖上一版。
 */
@Slf4j
public class EditModeDetectNode {

    private static final int MAX_FILE_LIST = 80;

    private static final Set<String> SKIP_DIR_NAMES = Set.of(
            "node_modules", "dist", "build", ".git", ".idea", ".vscode", "target"
    );

    /** 用户明确要新图时的关键词，只做分流，不调模型分类 */
    private static final String[] IMAGE_HINTS = {
            "图片", "配图", "插画", "插图", "素材", "封面", "头像", "换图", "搜图",
            "logo", "图标", "illustration", "image"
    };

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            log.info("执行节点: 识别生成模式");

            Long appId = context.getAppId();
            CodeGenTypeEnum generationType = context.getGenerationType();
            boolean needNewImages = promptWantsNewImages(context.getOriginalPrompt());
            context.setNeedNewImages(needNewImages);

            if (appId == null || appId <= 0 || generationType == null) {
                markCreateMode(context, "缺少 appId 或生成类型，按首次生成");
                return WorkflowContext.saveContext(context);
            }

            AppVersionService appVersionService = SpringContextUtil.getBean(AppVersionService.class);
            String latestVersionDir = appVersionService.getLatestCodeDir(appId);
            if (StrUtil.isBlank(latestVersionDir)) {
                markCreateMode(context, "尚无版本记录，按首次生成");
                return WorkflowContext.saveContext(context);
            }

            String existingCodeDir = resolveCodeDir(generationType, appId, latestVersionDir);
            if (!hasSourceFiles(existingCodeDir)) {
                markCreateMode(context, "版本目录为空或不存在，按首次生成");
                return WorkflowContext.saveContext(context);
            }

            context.setEditMode(true);
            context.setExistingCodeDir(existingCodeDir);
            context.setExistingFileSummary(buildFileSummary(existingCodeDir));
            context.setCurrentStep("识别生成模式");
            log.info("判定为修改模式 appId={}, codeDir={}, needNewImages={}",
                    appId, existingCodeDir, needNewImages);
            return WorkflowContext.saveContext(context);
        });
    }

    private static void markCreateMode(WorkflowContext context, String reason) {
        context.setEditMode(false);
        context.setExistingCodeDir(null);
        context.setExistingFileSummary(null);
        context.setCurrentStep("识别生成模式");
        log.info("{}，appId={}", reason, context.getAppId());
    }

    private static boolean promptWantsNewImages(String prompt) {
        if (StrUtil.isBlank(prompt)) {
            return false;
        }
        String lower = prompt.toLowerCase(Locale.ROOT);
        for (String hint : IMAGE_HINTS) {
            if (lower.contains(hint.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 与 {@link CodeGeneratorNode} 落盘规则一致，用来读上一版，不是写这一版。
     */
    private static String resolveCodeDir(CodeGenTypeEnum generationType, Long appId, String versionDir) {
        if (generationType == CodeGenTypeEnum.VUE_PROJECT) {
            VueProjectVersionManager versionManager = SpringContextUtil.getBean(VueProjectVersionManager.class);
            return versionManager.getVersionDir(appId, versionDir).getAbsolutePath();
        }
        return String.format("%s/%s_%s_%s",
                AppConstant.CODE_OUTPUT_ROOT_DIR, generationType.getValue(), appId, versionDir);
    }

    private static boolean hasSourceFiles(String codeDir) {
        if (StrUtil.isBlank(codeDir)) {
            return false;
        }
        File dir = new File(codeDir);
        return dir.isDirectory() && StrUtil.isNotBlank(buildFileSummary(codeDir));
    }

    /**
     * 扫相对路径当目录索引，不把文件正文塞进提示词。
     */
    private static String buildFileSummary(String codeDir) {
        Path root = Path.of(codeDir);
        List<String> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root, 8)) {
            walk.filter(Files::isRegularFile)
                    .filter(path -> !isSkipped(root, path))
                    .limit(MAX_FILE_LIST)
                    .forEach(path -> files.add(root.relativize(path).toString().replace('\\', '/')));
        } catch (Exception e) {
            log.warn("扫描已有代码目录失败: {}", codeDir, e);
            return "";
        }
        return String.join("\n", files);
    }

    private static boolean isSkipped(Path root, Path file) {
        Path relative = root.relativize(file);
        for (Path part : relative) {
            if (SKIP_DIR_NAMES.contains(part.toString())) {
                return true;
            }
        }
        String name = file.getFileName().toString();
        return name.endsWith(".log") || name.endsWith(".tmp") || ".DS_Store".equals(name);
    }
}

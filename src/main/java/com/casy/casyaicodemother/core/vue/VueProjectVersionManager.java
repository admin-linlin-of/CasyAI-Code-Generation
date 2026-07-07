package com.casy.casyaicodemother.core.vue;

import cn.hutool.core.io.FileUtil;
import com.casy.casyaicodemother.constant.AppConstant;
import com.casy.casyaicodemother.model.enums.CodeGenTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Vue 多版本目录管理：复制上一版源码、解析路径、定位共用依赖目录。
 * <p>
 * 磁盘布局示意：
 * <pre>
 * tmp/code_output/
 *   vue_project_{appId}_shared/     ← 全应用共用 node_modules（npm install 只在这里）
 *     package.json
 *     package-lock.json
 *     node_modules/
 *   vue_project_{appId}_v1/         ← 版本快照：源码 + 各自 dist，不含 node_modules
 *   vue_project_{appId}_v2/         ← 从 v1 复制而来，AI 再覆盖修改的文件
 * </pre>
 * <p>
 * 多版本生成流程（配合 {@link com.casy.casyaicodemother.service.impl.AppVersionServiceImpl#createCodeVersion}）：
 * <pre>
 * 用户发消息
 *   → AI 首次 writeFile
 *   → createCodeVersion
 *        ├─ v1：仅创建空目录
 *        └─ v2+：copyVersionProject(上一版 → 新版)，排除 node_modules/dist
 *   → AI 写入/覆盖文件（可能只改一个 .vue）
 *   → 流结束 → 前端调用 /tAppVersion/build → VueProjectBuilder
 *        ├─ 同步 package.json 到 shared
 *        ├─ shared 里 npm install（依赖未变则跳过）
 *        ├─ 版本目录 node_modules → junction/软链 → shared/node_modules
 *        └─ 版本目录 npm run build → 产出写入该版本 dist/
 * </pre>
 */
@Slf4j
@Component
public class VueProjectVersionManager {

    /** 复制版本时跳过的目录（体积大或可重建） */
    public static final Set<String> COPY_EXCLUDE_DIR_NAMES = Set.of("node_modules", "dist");

    private static final Pattern VERSION_PATH_PATTERN = Pattern.compile("vue_project_(\\d+)_(v\\d+)");

    public String getSharedDirName(Long appId) {
        return String.format("%s_%s_shared", CodeGenTypeEnum.VUE_PROJECT.getValue(), appId);
    }

    public String getVersionDirName(Long appId, String codeDir) {
        return String.format("%s_%s_%s", CodeGenTypeEnum.VUE_PROJECT.getValue(), appId, codeDir);
    }

    public File getSharedDir(Long appId) {
        return new File(AppConstant.CODE_OUTPUT_ROOT_DIR, getSharedDirName(appId));
    }

    public File getVersionDir(Long appId, String codeDir) {
        return new File(AppConstant.CODE_OUTPUT_ROOT_DIR, getVersionDirName(appId, codeDir));
    }

    /**
     * 新建版本目录：v1 建空目录；v2+ 从上一版完整复制源码（不含 node_modules/dist）。
     *
     * @param appId            应用 ID
     * @param newCodeDir       新版本目录名，如 v2
     * @param previousCodeDir  上一版本目录名，如 v1；首次生成时为 null
     */
    public void initializeNewVersionDirectory(Long appId, String newCodeDir, String previousCodeDir) {
        File targetDir = getVersionDir(appId, newCodeDir);
        if (previousCodeDir == null) {
            FileUtil.mkdir(targetDir);
            log.info("首次版本 {}，创建空项目目录：{}", newCodeDir, targetDir.getAbsolutePath());
            return;
        }
        File sourceDir = getVersionDir(appId, previousCodeDir);
        if (!sourceDir.exists()) {
            log.warn("上一版本目录 {} 不存在，改为创建空目录：{}", previousCodeDir, targetDir.getAbsolutePath());
            FileUtil.mkdir(targetDir);
            return;
        }
        copyVersionProject(sourceDir, targetDir);
        log.info("已从 {} 复制到 {}（排除 node_modules、dist）", sourceDir.getAbsolutePath(), targetDir.getAbsolutePath());
    }

    /**
     * 递归复制项目文件，跳过 node_modules 与 dist。
     */
    public void copyVersionProject(File sourceDir, File targetDir) {
        Path sourcePath = sourceDir.toPath();
        Path targetPath = targetDir.toPath();
        try {
            if (Files.exists(targetPath)) {
                FileUtil.del(targetDir);
            }
            Files.createDirectories(targetPath);
            Files.walkFileTree(sourcePath, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    Path relative = sourcePath.relativize(dir);
                    if (relative.getNameCount() > 0 && shouldSkipRelativePath(relative)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    Path destDir = targetPath.resolve(relative);
                    if (!Files.exists(destDir)) {
                        Files.createDirectories(destDir);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Path relative = sourcePath.relativize(file);
                    if (shouldSkipRelativePath(relative)) {
                        return FileVisitResult.CONTINUE;
                    }
                    Path destFile = targetPath.resolve(relative);
                    Files.createDirectories(destFile.getParent());
                    Files.copy(file, destFile, StandardCopyOption.REPLACE_EXISTING);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new RuntimeException("复制版本项目失败：" + sourceDir + " -> " + targetDir, e);
        }
    }

    /**
     * 从版本目录路径解析 appId，例如 .../vue_project_421860696638402560_v2 → 421860696638402560
     */
    public Long extractAppIdFromProjectPath(String projectPath) {
        if (projectPath == null) {
            return null;
        }
        Matcher matcher = VERSION_PATH_PATTERN.matcher(projectPath.replace('\\', '/'));
        if (matcher.find()) {
            return Long.parseLong(matcher.group(1));
        }
        return null;
    }

    public String extractCodeDirFromProjectPath(String projectPath) {
        if (projectPath == null) {
            return null;
        }
        Matcher matcher = VERSION_PATH_PATTERN.matcher(projectPath.replace('\\', '/'));
        if (matcher.find()) {
            return matcher.group(2);
        }
        return null;
    }

    private boolean shouldSkipRelativePath(Path relative) {
        if (relative.getNameCount() == 0) {
            return false;
        }
        String topLevel = relative.getName(0).toString();
        return COPY_EXCLUDE_DIR_NAMES.contains(topLevel);
    }
}

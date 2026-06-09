package com.casy.casyaicodemother.core.builder;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RuntimeUtil;
import com.casy.casyaicodemother.core.vue.VueProjectNodeModulesLinker;
import com.casy.casyaicodemother.core.vue.VueProjectVersionManager;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

/**
 * Vue 项目构建器：共用 node_modules + 各版本独立 dist。
 * <p>
 * 构建步骤（每个版本目录各自 npm run build，依赖走 shared）：
 * <ol>
 *   <li>从路径解析 appId</li>
 *   <li>将当前版本的 package.json / package-lock.json 同步到 shared 目录</li>
 *   <li>若 shared/node_modules 不存在或依赖文件有变化 → 仅在 shared 执行 npm install</li>
 *   <li>在版本目录创建 node_modules → shared/node_modules 的链接（Windows junction / Linux symlink）</li>
 *   <li>在版本目录执行 npm run build，dist 写入该版本目录</li>
 * </ol>
 */
@Slf4j
@Component
public class VueProjectBuilder {

    @Resource
    private VueProjectVersionManager versionManager;

    @Resource
    private VueProjectNodeModulesLinker nodeModulesLinker;

    /**
     * 异步构建项目（不阻塞 SSE 流结束响应）
     */
    public void buildProjectAsync(String projectPath) {
        Thread.ofVirtual().name("vue-builder-" + System.currentTimeMillis()).start(() -> {
            try {
                buildProject(projectPath);
            } catch (Exception e) {
                log.error("异步构建 Vue 项目时发生异常：{}", e.getMessage(), e);
            }
        });
    }

    /**
     * 构建指定版本目录下的 Vue 项目
     *
     * @param projectPath 版本项目根路径，如 tmp/code_output/vue_project_123_v2
     */
    public boolean buildProject(String projectPath) {
        File projectDir = new File(projectPath);
        if (!projectDir.exists() || !projectDir.isDirectory()) {
            log.error("项目目录不存在：{}", projectDir);
            return false;
        }
        File packageJson = new File(projectDir, "package.json");
        if (!packageJson.exists()) {
            log.error("package.json 不存在：{}", packageJson.getAbsolutePath());
            return false;
        }

        Long appId = versionManager.extractAppIdFromProjectPath(projectPath);
        if (appId == null) {
            log.error("无法从路径解析 appId，跳过共用依赖逻辑：{}", projectPath);
            return buildProjectLegacy(projectDir);
        }

        File sharedDir = versionManager.getSharedDir(appId);
        log.info("开始构建 Vue 项目：{}，共用依赖目录：{}", projectPath, sharedDir.getAbsolutePath());

        try {
            // 1. 依赖描述文件同步到 shared，按需 install（全版本只维护一份 node_modules）
            boolean depsChanged = syncDependenciesToShared(projectDir, sharedDir);
            File sharedNodeModules = new File(sharedDir, "node_modules");
            if (!sharedNodeModules.exists() || depsChanged) {
                if (!executeNpmInstall(sharedDir)) {
                    log.error("shared 目录 npm install 失败：{}", sharedDir.getAbsolutePath());
                    return false;
                }
            }
            // 2. 版本目录通过链接使用 shared/node_modules，避免每版复制 1G+ 依赖
            nodeModulesLinker.ensureNodeModulesLink(projectDir.toPath(), sharedNodeModules.toPath());
        } catch (IOException e) {
            log.error("准备共用 node_modules 失败：{}", e.getMessage(), e);
            return false;
        }

        // 3. 在版本目录 build，产物 dist 属于该版本
        if (!executeNpmBuild(projectDir)) {
            log.error("npm run build 失败：{}", projectDir.getAbsolutePath());
            return false;
        }

        File distDir = new File(projectDir, "dist");
        if (!distDir.exists()) {
            log.error("构建完成但 dist 未生成：{}", distDir.getAbsolutePath());
            return false;
        }
        log.info("Vue 项目构建成功，dist：{}", distDir.getAbsolutePath());
        return true;
    }

    /**
     * 将版本目录的 package.json、package-lock.json 同步到 shared。
     *
     * @return 是否有文件被更新（有更新则需要重新 npm install）
     */
    private boolean syncDependenciesToShared(File versionDir, File sharedDir) {
        FileUtil.mkdir(sharedDir);
        boolean changed = false;
        changed |= copyIfDifferent(new File(versionDir, "package.json"), new File(sharedDir, "package.json"));
        changed |= copyIfDifferent(new File(versionDir, "package-lock.json"), new File(sharedDir, "package-lock.json"));
        return changed;
    }

    /**
     * 比较两个文件内容
     * 不一样（或目标不存在）→ 复制过去，返回 true
     * 一样 → 不复制，返回 false
     *
     * @param source 源文件
     * @param target 目标文件
     * @return 返回是否复核
     */
    private boolean copyIfDifferent(File source, File target) {
        if (!source.exists()) {
            return false;
        }
        if (!target.exists()) {
            FileUtil.copy(source, target, true);
            return true;
        }
        try {
            if (Files.mismatch(source.toPath(), target.toPath()) != -1L) {
                FileUtil.copy(source, target, true);
                return true;
            }
        } catch (IOException e) {
            FileUtil.copy(source, target, true);
            return true;
        }
        return false;
    }

    /** 无法解析 appId 时的降级：仍在当前目录 install + build（兼容旧目录结构） */
    private boolean buildProjectLegacy(File projectDir) {
        if (!executeNpmInstall(projectDir)) {
            return false;
        }
        if (!executeNpmBuild(projectDir)) {
            return false;
        }
        return new File(projectDir, "dist").exists();
    }

    private boolean executeCommand(File workingDir, String command, int timeoutSeconds) {
        try {
            log.info("在目录 {} 执行：{}", workingDir.getAbsolutePath(), command);
            Process process = RuntimeUtil.exec(null, workingDir, command.split("\\s+"));
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                log.error("命令超时（{}秒）：{}", timeoutSeconds, command);
                process.destroyForcibly();
                return false;
            }
            if (process.exitValue() == 0) {
                log.info("命令成功：{}", command);
                return true;
            }
            log.error("命令失败，exitCode={}：{}", process.exitValue(), command);
            return false;
        } catch (Exception e) {
            log.error("执行命令异常：{}，{}", command, e.getMessage());
            return false;
        }
    }

    private boolean executeNpmInstall(File workingDir) {
        log.info("shared/版本目录 {} 执行 npm install", workingDir.getAbsolutePath());
        return executeCommand(workingDir, buildCommand("npm") + " install", 300);
    }

    private boolean executeNpmBuild(File projectDir) {
        log.info("版本目录 {} 执行 npm run build", projectDir.getAbsolutePath());
        return executeCommand(projectDir, buildCommand("npm") + " run build", 300);
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("windows");
    }

    private String buildCommand(String baseCommand) {
        return isWindows() ? baseCommand + ".cmd" : baseCommand;
    }
}

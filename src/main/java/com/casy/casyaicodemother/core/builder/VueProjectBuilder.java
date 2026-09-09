package com.casy.casyaicodemother.core.builder;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.RuntimeUtil;
import cn.hutool.core.util.StrUtil;
import com.casy.casyaicodemother.core.vue.VueProjectNodeModulesLinker;
import com.casy.casyaicodemother.core.vue.VueProjectVersionManager;
import com.casy.casyaicodemother.langgraph4j.node.SitePreviewNode;
import com.casy.casyaicodemother.model.enums.CodeGenTypeEnum;
import com.casy.casyaicodemother.model.enums.VersionBuildStatusEnum;
import com.casy.casyaicodemother.service.AppVersionService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
 * <p>
 * 构建失败时会将 npm 输出摘要写入 t_app_version.build_error，供前端展示。
 */
@Slf4j
@Component
public class VueProjectBuilder {

    /** 写入数据库的 build_error 最大长度，需不超过 t_app_version.build_error（varchar 500） */
    private static final int BUILD_ERROR_MAX_LEN = 500;

    @Resource
    private VueProjectVersionManager versionManager;

    @Resource
    private VueProjectNodeModulesLinker nodeModulesLinker;

    @Resource
    @Lazy
    private AppVersionService appVersionService;

    /**
     * 异步构建项目（不阻塞 SSE 流结束响应）
     */
    public void buildProjectAsync(String projectPath) {
        Thread.ofVirtual().name("vue-builder-" + System.currentTimeMillis()).start(() -> {
            try {
                buildProject(projectPath);
            } catch (Exception e) {
                log.error("异步构建 Vue 项目时发生异常：{}", e.getMessage(), e);
                markBuildFailed(projectPath, e.getMessage());
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
            String error = "项目目录不存在：" + projectPath;
            log.error(error);
            markBuildFailed(projectPath, error);
            return false;
        }
        File packageJson = new File(projectDir, "package.json");
        if (!packageJson.exists()) {
            String error = "package.json 不存在：" + packageJson.getAbsolutePath();
            log.error(error);
            markBuildFailed(projectPath, error);
            return false;
        }

        Long appId = versionManager.extractAppIdFromProjectPath(projectPath);
        String codeDir = versionManager.extractCodeDirFromProjectPath(projectPath);
        if (appId != null && codeDir != null) {
            // 开始构建：清空历史 build_error，状态置为 building
            appVersionService.updateBuildStatus(appId, codeDir, VersionBuildStatusEnum.BUILDING);
        }

        BuildOutcome outcome;
        if (appId == null) {
            log.error("无法从路径解析 appId，跳过共用依赖逻辑：{}", projectPath);
            outcome = buildProjectLegacy(projectDir);
        } else {
            outcome = doBuildProject(projectPath, projectDir, appId);
        }
        return finishBuild(appId, codeDir, outcome);
    }

    /**
     * 标记构建失败，并将失败原因持久化到 t_app_version.build_error。
     */
    private void markBuildFailed(String projectPath, String buildError) {
        Long appId = versionManager.extractAppIdFromProjectPath(projectPath);
        String codeDir = versionManager.extractCodeDirFromProjectPath(projectPath);
        if (appId != null && codeDir != null) {
            appVersionService.updateBuildStatus(appId, codeDir, VersionBuildStatusEnum.FAILED, buildError);
        }
    }

    /**
     * 根据构建结果更新版本状态；失败时附带 error 写入 build_error。
     * 打包成功后提交封面截图：传统生成走异步 build，原先不会进工作流的 SitePreviewNode，
     * 首页卡片封面会一直空着。工作流里 ProjectBuilderNode 还会再 submit 一次，同 appId 会复用任务。
     */
    private boolean finishBuild(Long appId, String codeDir, BuildOutcome outcome) {
        if (appId != null && codeDir != null) {
            if (outcome.success) {
                appVersionService.updateBuildStatus(appId, codeDir, VersionBuildStatusEnum.SUCCESS);
                File versionDir = versionManager.getVersionDir(appId, codeDir);
                SitePreviewNode.submit(appId, versionDir.getAbsolutePath(), CodeGenTypeEnum.VUE_PROJECT);
            } else {
                appVersionService.updateBuildStatus(appId, codeDir, VersionBuildStatusEnum.FAILED, outcome.error);
            }
        }
        return outcome.success;
    }

    private BuildOutcome doBuildProject(String projectPath, File projectDir, Long appId) {
        File sharedDir = versionManager.getSharedDir(appId);
        log.info("开始构建 Vue 项目：{}，共用依赖目录：{}", projectPath, sharedDir.getAbsolutePath());

        try {
            boolean depsChanged = syncDependenciesToShared(projectDir, sharedDir);
            File sharedNodeModules = new File(sharedDir, "node_modules");
            if (!sharedNodeModules.exists() || depsChanged) {
                CommandResult installResult = executeNpmInstall(sharedDir);
                if (!installResult.ok) {
                    log.error("shared 目录 npm install 失败：{}", sharedDir.getAbsolutePath());
                    return BuildOutcome.fail("npm install 失败（shared 目录）：\n" + installResult.errorDetail);
                }
            }
            nodeModulesLinker.ensureNodeModulesLink(projectDir.toPath(), sharedNodeModules.toPath());
        } catch (IOException e) {
            log.error("准备共用 node_modules 失败：{}", e.getMessage(), e);
            return BuildOutcome.fail("准备 node_modules 失败：" + e.getMessage());
        }

        CommandResult buildResult = executeNpmBuild(projectDir);
        if (!buildResult.ok) {
            log.error("npm run build 失败：{}", projectDir.getAbsolutePath());
            return BuildOutcome.fail("npm run build 失败：\n" + buildResult.errorDetail);
        }

        File distDir = new File(projectDir, "dist");
        if (!distDir.exists()) {
            String error = "构建完成但 dist 未生成：" + distDir.getAbsolutePath();
            log.error(error);
            return BuildOutcome.fail(error);
        }
        log.info("Vue 项目构建成功，dist：{}", distDir.getAbsolutePath());
        return BuildOutcome.ok();
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
    private BuildOutcome buildProjectLegacy(File projectDir) {
        CommandResult installResult = executeNpmInstall(projectDir);
        if (!installResult.ok) {
            return BuildOutcome.fail("npm install 失败：\n" + installResult.errorDetail);
        }
        CommandResult buildResult = executeNpmBuild(projectDir);
        if (!buildResult.ok) {
            return BuildOutcome.fail("npm run build 失败：\n" + buildResult.errorDetail);
        }
        if (!new File(projectDir, "dist").exists()) {
            return BuildOutcome.fail("构建完成但 dist 未生成");
        }
        return BuildOutcome.ok();
    }

    /**
     * 在指定目录执行 shell 命令，并捕获 stdout/stderr 供失败时展示。
     *
     * @return CommandResult.success 表示 exitCode=0；失败时 errorDetail 含 exitCode 与 npm 输出
     */
    private CommandResult executeCommand(File workingDir, String command, int timeoutSeconds) {
        try {
            log.info("在目录 {} 执行：{}", workingDir.getAbsolutePath(), command);
            Process process = RuntimeUtil.exec(null, workingDir, command.split("\\s+"));
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            String stdout = readStream(process.getInputStream());
            String stderr = readStream(process.getErrorStream());
            if (!finished) {
                log.error("命令超时（{}秒）：{}", timeoutSeconds, command);
                process.destroyForcibly();
                return CommandResult.fail("命令执行超时（" + timeoutSeconds + "秒）：" + command);
            }
            if (process.exitValue() == 0) {
                log.info("命令成功：{}", command);
                return CommandResult.success();
            }
            log.error("命令失败，exitCode={}：{}", process.exitValue(), command);
            return CommandResult.fail(formatCommandError(command, process.exitValue(), stdout, stderr));
        } catch (Exception e) {
            log.error("执行命令异常：{}，{}", command, e.getMessage());
            return CommandResult.fail("执行命令异常：" + e.getMessage());
        }
    }

    /** 读取进程输出流为 UTF-8 字符串 */
    private String readStream(InputStream stream) {
        if (stream == null) {
            return "";
        }
        return IoUtil.read(stream, StandardCharsets.UTF_8);
    }

    /** 拼接 exitCode、stderr、stdout，供前端 build_error 展示 */
    private String formatCommandError(String command, int exitCode, String stdout, String stderr) {
        StringBuilder sb = new StringBuilder();
        sb.append("exitCode=").append(exitCode).append("，命令：").append(command);
        if (StrUtil.isNotBlank(stderr)) {
            sb.append("\n").append(stderr.trim());
        }
        if (StrUtil.isNotBlank(stdout)) {
            sb.append("\n").append(stdout.trim());
        }
        return truncateError(sb.toString());
    }

    /** 截断过长错误信息，避免超出 build_error 字段上限 */
    private String truncateError(String error) {
        if (StrUtil.isBlank(error)) {
            return "打包失败，请查看服务端日志";
        }
        return StrUtil.sub(error, 0, BUILD_ERROR_MAX_LEN);
    }

    private CommandResult executeNpmInstall(File workingDir) {
        log.info("shared/版本目录 {} 执行 npm install", workingDir.getAbsolutePath());
        return executeCommand(workingDir, buildCommand("npm") + " install", 300);
    }

    private CommandResult executeNpmBuild(File projectDir) {
        log.info("版本目录 {} 执行 npm run build", projectDir.getAbsolutePath());
        return executeCommand(projectDir, buildCommand("npm") + " run build", 300);
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("windows");
    }

    private String buildCommand(String baseCommand) {
        return isWindows() ? baseCommand + ".cmd" : baseCommand;
    }

    /** npm 命令执行结果：ok=true 成功；失败时 errorDetail 为可展示的错误摘要 */
    private record CommandResult(boolean ok, String errorDetail) {
        /** 工厂方法不可命名为 ok()，会与 record 访问器 ok() 冲突 */
        static CommandResult success() {
            return new CommandResult(true, null);
        }

        static CommandResult fail(String errorDetail) {
            return new CommandResult(false, errorDetail);
        }
    }

    /** 整次 buildProject 的结果，用于统一写入 build_status / build_error */
    private record BuildOutcome(boolean success, String error) {
        static BuildOutcome ok() {
            return new BuildOutcome(true, null);
        }

        static BuildOutcome fail(String error) {
            return new BuildOutcome(false, error);
        }
    }
}

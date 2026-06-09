package com.casy.casyaicodemother.core.vue;

import cn.hutool.core.io.FileUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 为各版本目录创建指向「共用 node_modules」的目录链接。
 * <p>
 * Windows 使用 junction（目录联接，普通用户可用），Linux/macOS 使用符号软链接。
 * 调用方只需 {@link #ensureNodeModulesLink(Path, Path)}，无需关心操作系统差异。
 */
@Slf4j
@Component
public class VueProjectNodeModulesLinker {

    /**
     * 确保 versionDir/node_modules 指向 sharedNodeModules。
     * <p>
     * 若已存在且指向正确则跳过；若存在但指向错误或为真实目录则删除后重建链接。
     *
     * @param versionDir         版本项目根目录，如 vue_project_123_v2
     * @param sharedNodeModules  共用依赖目录，如 vue_project_123_shared/node_modules
     */
    public void ensureNodeModulesLink(Path versionDir, Path sharedNodeModules) throws IOException {
        Path linkPath = versionDir.resolve("node_modules");
        Path absoluteTarget = sharedNodeModules.toAbsolutePath().normalize();

        if (Files.exists(linkPath)) {
            if (isLinkPointingTo(linkPath, absoluteTarget)) {
                log.debug("node_modules 链接已存在且正确：{}", linkPath);
                return;
            }
            log.info("删除旧的 node_modules（真实目录或错误链接）：{}", linkPath);
            FileUtil.del(linkPath.toFile());
        }

        Files.createDirectories(absoluteTarget.getParent());
        if (!Files.exists(absoluteTarget)) {
            Files.createDirectories(absoluteTarget);
        }

        if (isWindows()) {
            createWindowsJunction(linkPath, absoluteTarget);
        } else {
            Files.createSymbolicLink(linkPath, absoluteTarget);
        }
        log.info("已创建 node_modules 链接：{} -> {}", linkPath, absoluteTarget);
    }

    private boolean isLinkPointingTo(Path linkPath, Path expectedTarget) throws IOException {
        if (!Files.exists(linkPath)) {
            return false;
        }
        try {
            Path realLink = linkPath.toRealPath();
            Path realTarget = expectedTarget.toRealPath();
            return Files.isSameFile(realLink, realTarget);
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Windows：mklink /J 链接路径 目标路径（Junction 仅用于目录）
     */
    private void createWindowsJunction(Path linkPath, Path targetPath) throws IOException {
        FileUtil.mkdir(linkPath.getParent());
        String[] command = {
                "cmd", "/c", "mklink", "/J",
                linkPath.toAbsolutePath().toString(),
                targetPath.toAbsolutePath().toString()
        };
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            boolean finished = process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished || process.exitValue() != 0) {
                throw new IOException("mklink /J 失败，exitCode=" + (finished ? process.exitValue() : "timeout"));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("mklink /J 被中断", e);
        }
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("windows");
    }
}

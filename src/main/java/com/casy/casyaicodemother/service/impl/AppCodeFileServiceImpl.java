package com.casy.casyaicodemother.service.impl;

import cn.hutool.core.util.StrUtil;
import com.casy.casyaicodemother.core.vue.VueProjectVersionManager;
import com.casy.casyaicodemother.exception.ErrorCode;
import com.casy.casyaicodemother.exception.ThrowUtils;
import com.casy.casyaicodemother.model.entity.App;
import com.casy.casyaicodemother.model.entity.User;
import com.casy.casyaicodemother.model.enums.CodeGenTypeEnum;
import com.casy.casyaicodemother.service.AppCodeFileService;
import com.casy.casyaicodemother.service.AppService;
import com.casy.casyaicodemother.util.AppAccessUtils;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Vue 项目源码文件列表服务。
 * <p>
 * 供前端代码预览面板构建目录树：递归扫描版本目录，返回相对路径列表。
 * 过滤规则与 {@link ProjectDownloadServiceImpl} 打包下载保持一致。
 */
@Service
public class AppCodeFileServiceImpl implements AppCodeFileService {

    /** 目录名黑名单：命中任一段路径即跳过（如 node_modules/dist） */
    private static final Set<String> IGNORED_NAMES = Set.of(
            "node_modules", ".git", "dist", "build", ".DS_Store", ".env", "target", ".mvn", ".idea", ".vscode"
    );

    /** 文件扩展名黑名单 */
    private static final Set<String> IGNORED_EXTENSIONS = Set.of(".log", ".tmp", ".cache");

    @Resource
    private AppService appService;

    @Resource
    private VueProjectVersionManager vueProjectVersionManager;

    @Override
    public List<String> listVueProjectFiles(Long appId, String codeDir, User loginUser) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(StrUtil.isBlank(codeDir), ErrorCode.PARAMS_ERROR, "版本目录不能为空");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);

        App app = appService.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        ThrowUtils.throwIf(!AppAccessUtils.canViewAppContent(app, loginUser),
                ErrorCode.NO_AUTH_ERROR, "无权查看该应用代码");
        ThrowUtils.throwIf(CodeGenTypeEnum.VUE_PROJECT != CodeGenTypeEnum.getEnumByValue(app.getCodeGenType()),
                ErrorCode.OPERATION_ERROR, "仅 Vue 项目支持文件列表");

        // 版本目录绝对路径，例如 .../vue_project_{appId}_v1
        File projectRoot = vueProjectVersionManager.getVersionDir(appId, codeDir);
        ThrowUtils.throwIf(!projectRoot.exists() || !projectRoot.isDirectory(),
                ErrorCode.NOT_FOUND_ERROR, "项目目录不存在");

        List<String> files = new ArrayList<>();
        // 首次调用 dir 与 projectRoot 相同；递归进入子目录后 dir 会变，projectRoot 始终指向项目根
        collectFiles(projectRoot, projectRoot, files);
        files.sort(Comparator.naturalOrder());
        return files;
    }

    /**
     * 深度优先递归收集文件相对路径。
     *
     * @param dir         当前正在遍历的目录（递归时会变为 src/、src/components/ 等子目录）
     * @param projectRoot 项目根目录，全程不变，用于：
     *                    1. 计算相对路径（projectRoot.relativize → src/App.vue）
     *                    2. 路径过滤时获取相对路径各段名称
     * @param result      输出列表，追加形如 "src/App.vue" 的相对路径
     */
    private void collectFiles(File dir, File projectRoot, List<String> result) {
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (!isPathAllowed(projectRoot.toPath(), child.toPath())) {
                continue;
            }
            if (child.isDirectory()) {
                // dir 下钻，projectRoot 保持根目录引用
                collectFiles(child, projectRoot, result);
            } else {
                // 统一为正斜杠，与前端路径及静态资源 URL 一致
                String relative = projectRoot.toPath().relativize(child.toPath()).toString().replace('\\', '/');
                result.add(relative);
            }
        }
    }

    /**
     * 判断路径是否允许出现在文件列表中。
     * 检查相对路径的每一段目录名和文件名，排除 node_modules、dist 及 .log 等。
     */
    private boolean isPathAllowed(Path projectRoot, Path fullPath) {
        Path relativePath = projectRoot.relativize(fullPath);
        for (Path part : relativePath) {
            String partName = part.toString();
            if (IGNORED_NAMES.contains(partName)) {
                return false;
            }
            if (IGNORED_EXTENSIONS.stream().anyMatch(partName::endsWith)) {
                return false;
            }
        }
        return true;
    }
}

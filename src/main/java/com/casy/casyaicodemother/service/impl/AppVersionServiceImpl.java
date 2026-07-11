package com.casy.casyaicodemother.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.casy.casyaicodemother.constant.AppConstant;
import com.casy.casyaicodemother.constant.UserConstant;
import com.casy.casyaicodemother.constant.AppConstant;
import com.casy.casyaicodemother.core.builder.VueProjectBuilder;
import com.casy.casyaicodemother.core.vue.VueProjectVersionManager;
import com.casy.casyaicodemother.exception.BusinessException;
import com.casy.casyaicodemother.exception.ErrorCode;
import com.casy.casyaicodemother.exception.ThrowUtils;
import com.casy.casyaicodemother.mapper.AppVersionMapper;
import com.casy.casyaicodemother.model.dto.app.AppVersionRequest;
import com.casy.casyaicodemother.model.entity.App;
import com.casy.casyaicodemother.model.entity.AppVersion;
import com.casy.casyaicodemother.model.entity.User;
import com.casy.casyaicodemother.model.enums.CodeGenTypeEnum;
import com.casy.casyaicodemother.model.enums.ModelTypeEnum;
import com.casy.casyaicodemother.model.enums.VersionBuildStatusEnum;
import com.casy.casyaicodemother.model.enums.VersionDeployStatusEnum;
import com.casy.casyaicodemother.service.AppService;
import com.casy.casyaicodemother.service.AppVersionService;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.util.List;

/**
 * 应用代码版本 服务层实现。
 *
 * @author <a href="https://gitee.com/linlinyes/casy-ai-code-mother">程序员Casy</a>
 */
@Service
public class AppVersionServiceImpl extends ServiceImpl<AppVersionMapper, AppVersion> implements AppVersionService {

    @Resource
    private AppService appService;

    @Resource
    @Lazy
    private AppVersionService appVersionService;

    @Resource
    private AppVersionMapper appVersionMapper;

    @Resource
    private VueProjectVersionManager vueProjectVersionManager;

    @Resource
    private VueProjectBuilder vueProjectBuilder;

    /**
     * 创建代码版本记录，并在磁盘上初始化版本目录。
     * <p>
     * v1：创建空目录，AI 从零写入全部文件。
     * v2+：先把上一版本完整复制到新目录（排除 node_modules、dist），AI 再只改需要改的文件。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createCodeVersion(Long appId, ModelTypeEnum modelTypeEnum, Long userMessageId) {
        ThrowUtils.throwIf(appId == null || modelTypeEnum == null, ErrorCode.PARAMS_ERROR);
        App appById = appService.getAppById(appId);
        ThrowUtils.throwIf(appById == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");

        AppVersionRequest appVersionRequest = new AppVersionRequest();
        appVersionRequest.setAppId(appId);
        appVersionRequest.setSortField("version_num");
        appVersionRequest.setSortOrder("ascend");
        List<AppVersion> appVersions = appVersionMapper.selectListByQuery(getQueryWrapper(appVersionRequest));

        int lastVersion = 1;
        String previousCodeDir = null;
        if (appVersions != null && !appVersions.isEmpty()) {
            if (appVersions.size() == 10) {
                AppVersion appVersion = appVersions.getFirst();
                appVersionService.removeByAppVersion(appVersion);
                appVersions = appVersions.subList(1, appVersions.size());
            }
            AppVersion last = appVersions.getLast();
            previousCodeDir = last.getCodeDir();
            lastVersion = last.getVersionNum() + 1;
        }

        String codeDir = String.format("v%s", lastVersion);
        /**
         * Long userId = StpUtil.getLoginIdAsLong();
         * 会报错SaTokenContext 上下文尚未初始化
         * 原因：
         * createCodeVersion 是在 AiCodeGeneratorFacade.processCodeStream 的 doOnComplete 里调用的。
         * Reactor 流完成回调跑在 Reactor 线程，不是发起请求的 HTTP 线程。
         * SaToken 靠 Filter 在请求线程里把登录信息放进 ThreadLocal，异步线程里没有这个上下文，
         * 所以 StpUtil.getLoginIdAsLong() 会报「SaTokenContext 上下文尚未初始化」。
         */
        Long userId = appById.getUserId();
        AppVersion appVersion = new AppVersion();
        appVersion.setVersionNum(lastVersion);
        appVersion.setCodeDir(codeDir);
        appVersion.setUserId(userId);
        appVersion.setModelType(modelTypeEnum.getModelName());
        appVersion.setAppId(appId);
        appVersion.setChatHistoryId(userMessageId);
        appVersion.setBuildStatus(VersionBuildStatusEnum.PENDING.getValue());
        appVersion.setDeployStatus(VersionDeployStatusEnum.NOT_DEPLOYED.getValue());
        this.save(appVersion);

        // 磁盘：v1 空目录；v2+ 从上一版复制源码（node_modules 走 shared，不在版本间复制）
        vueProjectVersionManager.initializeNewVersionDirectory(appId, codeDir, previousCodeDir);
        return codeDir;
    }


    @Override
    public void removeByAppVersion(AppVersion appVersion) {
        App appById = appService.getAppById(appVersion.getAppId());
        String dirName = String.format("%s_%s_%s", appById.getCodeGenType(), appVersion.getAppId(), appVersion.getCodeDir());
        String filePath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + dirName;
        File file = new File(filePath);
        ThrowUtils.throwIf(!file.exists(), ErrorCode.OPERATION_ERROR, "应用文件不存在");
        boolean deleted = FileUtil.del(file);
        if (!deleted) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "文件删除失败");
        }
        // 共用依赖目录 vue_project_{appId}_shared 不随单版本删除，避免其他版本失去 node_modules
        this.removeById(appVersion.getId());
    }

    @Override
    public List<AppVersion> getAppVersionsByAppId(Long appid, User loginUser) {
        ThrowUtils.throwIf(appid == null || appid <= 0, ErrorCode.PARAMS_ERROR);
        App app = appService.getAppById(appid);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        checkAppVersionViewAuth(app, loginUser);
        AppVersionRequest appVersionRequest = new AppVersionRequest();
        appVersionRequest.setAppId(appid);
        appVersionRequest.setSortField("version_num");
        appVersionRequest.setSortOrder("descend");
        return appVersionMapper.selectListByQuery(getQueryWrapper(appVersionRequest));
    }

    @Override
    public Page<AppVersion> listAppVersionByPage(AppVersionRequest appVersionRequest) {
        ThrowUtils.throwIf(appVersionRequest == null, ErrorCode.PARAMS_ERROR);
        int pageNum = appVersionRequest.getPageNum();
        int pageSize = appVersionRequest.getPageSize() <= 0 ? 10 : appVersionRequest.getPageSize();
        if (StrUtil.isBlank(appVersionRequest.getSortField())) {
            appVersionRequest.setSortField("create_time");
        }
        if (StrUtil.isBlank(appVersionRequest.getSortOrder())) {
            appVersionRequest.setSortOrder("descend");
        }
        return page(Page.of(pageNum, pageSize), getQueryWrapper(appVersionRequest));
    }

    @Override
    public AppVersion getByAppIdAndCodeDir(Long appId, String codeDir) {
        ThrowUtils.throwIf(appId == null || StrUtil.isBlank(codeDir), ErrorCode.PARAMS_ERROR);
        return getOne(QueryWrapper.create()
                .eq(AppVersion::getAppId, appId)
                .eq(AppVersion::getCodeDir, codeDir));
    }

    @Override
    public void updateBuildStatus(Long appId, String codeDir, VersionBuildStatusEnum buildStatus) {
        updateBuildStatus(appId, codeDir, buildStatus, null);
    }

    /**
     * 更新版本构建状态。
     * <ul>
     *   <li>BUILDING / SUCCESS → 清空 build_error</li>
     *   <li>FAILED → 写入 build_error（为空时使用默认文案）</li>
     * </ul>
     */
    @Override
    public void updateBuildStatus(Long appId, String codeDir, VersionBuildStatusEnum buildStatus, String buildError) {
        if (appId == null || StrUtil.isBlank(codeDir) || buildStatus == null) {
            return;
        }
        AppVersion appVersion = getByAppIdAndCodeDir(appId, codeDir);
        if (appVersion == null) {
            return;
        }
        AppVersion update = new AppVersion();
        update.setId(appVersion.getId());
        update.setBuildStatus(buildStatus.getValue());
        if (buildStatus == VersionBuildStatusEnum.SUCCESS || buildStatus == VersionBuildStatusEnum.BUILDING) {
            update.setBuildError(null);
        } else if (buildStatus == VersionBuildStatusEnum.FAILED) {
            update.setBuildError(StrUtil.blankToDefault(buildError, "打包失败，请查看服务端日志"));
        }
        updateById(update);
    }

    @Override
    public void updateDeployStatus(Long appId, String codeDir, VersionDeployStatusEnum deployStatus) {
        if (appId == null || StrUtil.isBlank(codeDir) || deployStatus == null) {
            return;
        }
        AppVersion appVersion = getByAppIdAndCodeDir(appId, codeDir);
        if (appVersion == null) {
            return;
        }
        AppVersion update = new AppVersion();
        update.setId(appVersion.getId());
        update.setDeployStatus(deployStatus.getValue());
        updateById(update);
    }

    @Override
    public String getLatestCodeDir(Long appId) {
        ThrowUtils.throwIf(appId == null, ErrorCode.PARAMS_ERROR);
        AppVersionRequest appVersionRequest = new AppVersionRequest();
        appVersionRequest.setAppId(appId);
        appVersionRequest.setSortField("version_num");
        appVersionRequest.setSortOrder("descend");
        List<AppVersion> appVersions = appVersionMapper.selectListByQuery(getQueryWrapper(appVersionRequest));
        if (CollUtil.isEmpty(appVersions)) {
            return null;
        }
        return appVersions.getFirst().getCodeDir();
    }

    @Override
    public void retryBuild(Long appId, String codeDir, User loginUser) {
        buildVersion(appId, codeDir, loginUser);
    }

    @Override
    public void buildVersion(Long appId, String codeDir, User loginUser) {
        ThrowUtils.throwIf(appId == null || StrUtil.isBlank(codeDir), ErrorCode.PARAMS_ERROR);
        App app = appService.getAppById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        checkAppVersionViewAuth(app, loginUser);
        ThrowUtils.throwIf(CodeGenTypeEnum.VUE_PROJECT != CodeGenTypeEnum.getEnumByValue(app.getCodeGenType()),
                ErrorCode.OPERATION_ERROR, "仅 Vue 项目支持打包");
        AppVersion appVersion = getByAppIdAndCodeDir(appId, codeDir);
        ThrowUtils.throwIf(appVersion == null, ErrorCode.NOT_FOUND_ERROR, "版本不存在");
        ThrowUtils.throwIf(VersionBuildStatusEnum.BUILDING.getValue().equals(appVersion.getBuildStatus()),
                ErrorCode.OPERATION_ERROR, "正在打包中，请稍候");
        String projectPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator
                + vueProjectVersionManager.getVersionDirName(appId, codeDir);
        File projectDir = new File(projectPath);
        ThrowUtils.throwIf(!projectDir.exists(), ErrorCode.NOT_FOUND_ERROR, "版本代码目录不存在");
        vueProjectBuilder.buildProjectAsync(projectPath);
    }

    private void checkAppVersionViewAuth(App app, User loginUser) {
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);
        if (UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole())) {
            return;
        }
        ThrowUtils.throwIf(!app.getUserId().equals(loginUser.getId()),
                ErrorCode.NO_AUTH_ERROR, "无权查看该应用版本");
    }

    /**
     * 获取查询包装类
     *
     * @param appVersionRequest 应用代码版本查询对象
     * @return 查询的对象
     */
    @Override
    public QueryWrapper getQueryWrapper(AppVersionRequest appVersionRequest) {
        QueryWrapper queryWrapper = QueryWrapper.create();
        if (appVersionRequest == null) {
            return queryWrapper;
        }
        Long id = appVersionRequest.getId();
        Integer versionNum = appVersionRequest.getVersionNum();
        String codeDir = appVersionRequest.getCodeDir();
        String modelType = appVersionRequest.getModelType();
        Long appId = appVersionRequest.getAppId();
        Long chatHistoryId = appVersionRequest.getChatHistoryId();
        Long userId = appVersionRequest.getUserId();
        String sortField = appVersionRequest.getSortField();
        String sortOrder = appVersionRequest.getSortOrder();
        // 拼接查询条件
        queryWrapper.eq(AppVersion::getId, id)
                .like(AppVersion::getVersionNum, versionNum)
                .eq(AppVersion::getCodeDir, codeDir)
                .eq(AppVersion::getAppId, appId)
                .eq(AppVersion::getModelType, modelType)
                .eq(AppVersion::getChatHistoryId, chatHistoryId)
                .eq(AppVersion::getUserId, userId)
                .orderBy(sortField, "ascend".equals(sortOrder));
        return queryWrapper;
    }

}

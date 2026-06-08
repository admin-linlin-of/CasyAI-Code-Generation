package com.casy.casyaicodemother.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.casy.casyaicodemother.constant.AppConstant;
import com.casy.casyaicodemother.constant.UserConstant;
import com.casy.casyaicodemother.exception.BusinessException;
import com.casy.casyaicodemother.exception.ErrorCode;
import com.casy.casyaicodemother.exception.ThrowUtils;
import com.casy.casyaicodemother.mapper.AppVersionMapper;
import com.casy.casyaicodemother.model.dto.app.AppVersionRequest;
import com.casy.casyaicodemother.model.entity.App;
import com.casy.casyaicodemother.model.entity.AppVersion;
import com.casy.casyaicodemother.model.entity.User;
import com.casy.casyaicodemother.model.enums.ModelTypeEnum;
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createCodeVersion(Long appId, ModelTypeEnum modelTypeEnum, Long userMessageId) {
        ThrowUtils.throwIf(appId == null || modelTypeEnum == null, ErrorCode.PARAMS_ERROR);
        // 获取应用数据
        App appById = appService.getAppById(appId);
        ThrowUtils.throwIf(appById == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        // 获取最新的应用版本
        AppVersionRequest appVersionRequest = new AppVersionRequest();
        appVersionRequest.setAppId(appId);
        appVersionRequest.setSortField("version_num");
        appVersionRequest.setSortOrder("ascend");
        List<AppVersion> appVersions = appVersionMapper.selectListByQuery(getQueryWrapper(appVersionRequest));
        int lastVersion = 1;
        if (appVersions != null && !appVersions.isEmpty()) {
            // 检查是否超过10个版本，超过10则删除最早的一个版本
            if (appVersions.size() == 10) {
                AppVersion appVersion = appVersions.getFirst();
                appVersionService.removeByAppVersion(appVersion);
            }
            AppVersion last = appVersions.getLast();
            lastVersion = last.getVersionNum() + 1;
        }
        // 代码目录
        String code_dir = String.format("v%s", lastVersion);
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
        appVersion.setCodeDir(code_dir);
        appVersion.setUserId(userId);
        appVersion.setModelType(modelTypeEnum.getModelName());
        appVersion.setAppId(appId);
        appVersion.setChatHistoryId(userMessageId);
        this.save(appVersion);
        return code_dir;
    }


    @Override
    public void removeByAppVersion(AppVersion appVersion) {
        // 获取文件目录
        App appById = appService.getAppById(appVersion.getAppId());
        String DirName = String.format("%s_%s_%s", appById.getCodeGenType(), appVersion.getAppId(), appVersion.getCodeDir());
        String filePath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + DirName;
        File file = new File(filePath);
        ThrowUtils.throwIf(!file.exists(), ErrorCode.OPERATION_ERROR, "应用文件不存在");
        boolean deleted = FileUtil.del(file);
        if (!deleted) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "文件删除失败");
        }
        // 删除版本记录
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

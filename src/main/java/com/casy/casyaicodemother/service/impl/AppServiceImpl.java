package com.casy.casyaicodemother.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.casy.casyaicodemother.constant.AppConstant;
import com.casy.casyaicodemother.constant.UserConstant;
import com.casy.casyaicodemother.core.AiCodeGeneratorFacade;
import com.casy.casyaicodemother.core.builder.VueProjectBuilder;
import com.casy.casyaicodemother.core.vue.VueProjectVersionManager;
import com.casy.casyaicodemother.core.handler.StreamHandlerExecutor;
import com.casy.casyaicodemother.exception.BusinessException;
import com.casy.casyaicodemother.exception.ErrorCode;
import com.casy.casyaicodemother.exception.ThrowUtils;
import com.casy.casyaicodemother.mapper.AppMapper;
import com.casy.casyaicodemother.model.dto.app.AppAddRequest;
import com.casy.casyaicodemother.model.dto.app.AppAdminUpdateRequest;
import com.casy.casyaicodemother.model.dto.app.AppQueryRequest;
import com.casy.casyaicodemother.model.dto.app.AppUpdateRequest;
import com.casy.casyaicodemother.model.entity.App;
import com.casy.casyaicodemother.model.entity.User;
import com.casy.casyaicodemother.model.enums.AppTypeEnum;
import com.casy.casyaicodemother.model.enums.CodeGenTypeEnum;
import com.casy.casyaicodemother.model.enums.ModelTypeEnum;
import com.casy.casyaicodemother.model.enums.VersionDeployStatusEnum;
import com.casy.casyaicodemother.model.vo.app.AppVO;
import com.casy.casyaicodemother.model.vo.user.UserVO;
import com.casy.casyaicodemother.service.*;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.io.File;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.casy.casyaicodemother.constant.AppConstant.APP_PUBLISHED;

/**
 * 应用 服务层实现。
 */
@Slf4j
@Service
public class AppServiceImpl extends ServiceImpl<AppMapper, App> implements AppService {

    @Resource
    private UserService userService;

    @Resource
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;

    @Resource
    private ChatHistoryService chatHistoryService;

    @Resource
    private StreamHandlerExecutor streamHandlerExecutor;

    @Resource
    private VueProjectBuilder vueProjectBuilder;

    @Resource
    @Lazy
    private AppVersionService appVersionService;

    @Resource
    private VueProjectVersionManager vueProjectVersionManager;

    @Resource
    private ScreenshotService screenshotService;

    @Override
    public long createApp(AppAddRequest appAddRequest, User loginUser) {
        ThrowUtils.throwIf(appAddRequest == null, ErrorCode.PARAMS_ERROR);
        String initPrompt = appAddRequest.getInitPrompt();
        ThrowUtils.throwIf(StrUtil.isBlank(initPrompt), ErrorCode.PARAMS_ERROR, "初始化提示词不能为空");
        String codeGenType = appAddRequest.getCodeGenType();
        if (StrUtil.isNotBlank(codeGenType)) {
            ThrowUtils.throwIf(CodeGenTypeEnum.getEnumByValue(codeGenType) == null,
                    ErrorCode.PARAMS_ERROR, "不存在生成类型");
        } else {
            codeGenType = CodeGenTypeEnum.MULTI_FILE.getValue();
        }
        App app = new App();
        app.setInitPrompt(initPrompt);
        app.setCodeGenType(codeGenType);
        app.setUserId(loginUser.getId());
        app.setPriority(0);
        app.setIsPublish(AppConstant.APP_NOT_PUBLISH);
        String appName = appAddRequest.getAppName();
        if (StrUtil.isBlank(appName)) {
            // 应用名称暂时为 initPrompt 前 12 位
            appName = initPrompt.substring(0, Math.min(initPrompt.length(), 12));
        }
        app.setAppName(appName);
        List<String> appTypes = appAddRequest.getAppTypes();
        if (CollUtil.isNotEmpty(appTypes)) {
            validateAppTypes(appTypes);
            app.setAppTypes(appTypes);
        } else {
            app.setAppTypes(new ArrayList<>());
        }
        boolean result = save(app);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return app.getId();
    }

    @Override
    public boolean updateApp(AppUpdateRequest appUpdateRequest, User loginUser) {
        ThrowUtils.throwIf(appUpdateRequest == null || appUpdateRequest.getId() == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(StrUtil.isBlank(appUpdateRequest.getAppName()), ErrorCode.PARAMS_ERROR, "应用名称不能为空");
        App oldApp = getAppById(appUpdateRequest.getId());
        checkAppAuth(oldApp, loginUser, "");
        App app = new App();
        app.setId(appUpdateRequest.getId());
        app.setAppName(appUpdateRequest.getAppName());
        if (appUpdateRequest.getAppTypes() != null) {
            validateAppTypes(appUpdateRequest.getAppTypes());
            app.setAppTypes(appUpdateRequest.getAppTypes());
        }
        if (appUpdateRequest.getIsPublish() != null) {
            validateIsPublish(appUpdateRequest.getIsPublish());
            app.setIsPublish(appUpdateRequest.getIsPublish());
        }
        app.setEditTime(LocalDateTime.now());
        return updateById(app);
    }

    @Override
    public boolean updateAppByAdmin(AppAdminUpdateRequest appAdminUpdateRequest) {
        ThrowUtils.throwIf(appAdminUpdateRequest == null || appAdminUpdateRequest.getId() == null, ErrorCode.PARAMS_ERROR);
        getAppById(appAdminUpdateRequest.getId());
        if (appAdminUpdateRequest.getAppTypes() != null) {
            validateAppTypes(appAdminUpdateRequest.getAppTypes());
        }
        if (appAdminUpdateRequest.getIsPublish() != null) {
            validateIsPublish(appAdminUpdateRequest.getIsPublish());
        }
        App app = new App();
        BeanUtil.copyProperties(appAdminUpdateRequest, app);
        return updateById(app);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteApp(long id, User loginUser) {
        App app = getAppById(id);
        checkAppAuth(app, loginUser, "");
        deleteAppFiles(app);
        chatHistoryService.deleteByAppId(id);
        return removeById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteAppByAdmin(long id) {
        App app = getAppById(id);
        deleteAppFiles(app);
        chatHistoryService.deleteByAppId(id);
        return removeById(id);
    }

    @Override
    public App getAppById(long id) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        App app = getById(id);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR);
        return app;
    }

    @Override
    public AppVO getAppVO(App app, User loginUser) {
        AppVO appVO = getAppVO(app);
        if (!canViewInitPrompt(app, loginUser)) {
            appVO.setInitPrompt(null);
        }
        return appVO;
    }

    @Override
    public AppVO getAppVO(App app) {
        if (app == null) {
            return null;
        }
        AppVO appVO = new AppVO();
        BeanUtil.copyProperties(app, appVO);
        return appVO;
    }

    @Override
    public List<AppVO> getAppVOList(List<App> appList) {
        if (CollUtil.isEmpty(appList)) {
            return new ArrayList<>();
        }
        // 批量获取用户信息，避免 N+1 查询问题
        Map<Long, UserVO> userVOMap = buildUserVOMap(appList.stream().map(App::getUserId).collect(Collectors.toSet()));
        return appList.stream().map(app -> {
            AppVO appVO = getAppVO(app);
            UserVO userVO = userVOMap.get(app.getUserId());
            appVO.setUser(userVO);
            return appVO;
        }).collect(Collectors.toList());
    }

    @Override
    public Page<AppVO> listMyAppVOByPage(AppQueryRequest appQueryRequest, User loginUser) {
        ThrowUtils.throwIf(appQueryRequest == null, ErrorCode.PARAMS_ERROR);
        int pageNum = appQueryRequest.getPageNum();
        ThrowUtils.throwIf(appQueryRequest.getPageSize() > 20, ErrorCode.PARAMS_ERROR, "每页最多查询 20 个应用");
        int pageSize = Math.min(appQueryRequest.getPageSize(), AppConstant.MAX_PAGE_SIZE);
        // 只查询当前用户的应用
        appQueryRequest.setUserId(loginUser.getId());
        QueryWrapper queryWrapper = getQueryWrapper(appQueryRequest);
        Page<App> appPage = page(Page.of(pageNum, pageSize), queryWrapper);
        return toAppVOPage(appPage, pageNum, pageSize, loginUser);
    }

    @Override
    public Page<AppVO> listGoodAppVOByPage(AppQueryRequest appQueryRequest) {
        ThrowUtils.throwIf(appQueryRequest == null, ErrorCode.PARAMS_ERROR);
        int pageNum = appQueryRequest.getPageNum();
        ThrowUtils.throwIf(appQueryRequest.getPageSize() > 20, ErrorCode.PARAMS_ERROR, "每页最多查询 20 个应用");
        int pageSize = Math.min(appQueryRequest.getPageSize(), AppConstant.MAX_PAGE_SIZE);
        // 只查询精选的应用
        appQueryRequest.setPriority(AppConstant.GOOD_APP_PRIORITY);
        // 应用必须公布
        appQueryRequest.setIsPublish(APP_PUBLISHED);
        QueryWrapper queryWrapper = getQueryWrapper(appQueryRequest);
        Page<App> appPage = page(Page.of(pageNum, pageSize), queryWrapper);
        return toAppVOPage(appPage, pageNum, pageSize, null);
    }

    @Override
    public Page<AppVO> listAppVOByPage(AppQueryRequest appQueryRequest, User loginUser) {
        ThrowUtils.throwIf(appQueryRequest == null, ErrorCode.PARAMS_ERROR);
        int pageNum = appQueryRequest.getPageNum();
        int pageSize = appQueryRequest.getPageSize();
        Page<App> appPage = page(Page.of(pageNum, pageSize), getQueryWrapper(appQueryRequest));
        return toAppVOPage(appPage, pageNum, pageSize, loginUser);
    }

    @Override
    public QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest) {
        if (appQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = appQueryRequest.getId();
        String appName = appQueryRequest.getAppName();
        String cover = appQueryRequest.getCover();
        String initPrompt = appQueryRequest.getInitPrompt();
        String codeGenType = appQueryRequest.getCodeGenType();
        List<String> appTypes = appQueryRequest.getAppTypes();
        String deployKey = appQueryRequest.getDeployKey();
        Integer priority = appQueryRequest.getPriority();
        Long userId = appQueryRequest.getUserId();
        Integer isPublish = appQueryRequest.getIsPublish();
        String sortField = appQueryRequest.getSortField();
        String sortOrder = appQueryRequest.getSortOrder();
        return QueryWrapper.create()
                .eq("id", id)
                .like("app_name", appName, StrUtil.isNotBlank(appName))
                .like("cover", cover, StrUtil.isNotBlank(cover))
                .like("init_prompt", initPrompt, StrUtil.isNotBlank(initPrompt))
                .eq("code_gen_type", codeGenType, StrUtil.isNotBlank(codeGenType))
                // WHERE (app_types::jsonb @> '["website"]'::jsonb)
                // @> 表示：左边 jsonb 必须包含右边 jsonb 的所有元素。
                .and(q -> q.and("app_types::jsonb @> ?::jsonb", JSONUtil.toJsonStr(appTypes)), CollUtil.isNotEmpty(appTypes))
                .eq("deploy_key", deployKey, StrUtil.isNotBlank(deployKey))
                .ge("priority", priority, priority != null)
                .eq("user_id", userId)
                .eq("is_publish", isPublish, isPublish != null)
                .orderBy(sortField, "ascend".equals(sortOrder));
    }

    /**
     * 代码生成
     *
     * <h3>工具调用消息流程图：</h3>
     * <img src="../../../../../../javadoc/doc-files/代码生成不同类型的流程.png" alt="登录验证流程" width="700"  height="500"/>
     * @param appId 应用ID
     * @param message 用户提示词
     * @param modelType 模型类型
     * @param loginUser 登录用户
     * @return 消息流
     */
    @Override
    public Flux<String> chatToGenCode(Long appId, String message, String modelType, User loginUser) {
        // 1. 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "用户消息不能为空");   // 1.参数校验
        // 2. 查询应用信息
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        // 3. 验证用户是否有权限访问该应用，仅本人可以生成代码
        checkAppAuth(app, loginUser, "");
        // 4. 获取应用的代码生成类型
        String codeGenTypeStr = app.getCodeGenType();
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenTypeStr);
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的代码生成类型");
        }
        ModelTypeEnum modelTypeEnum = ModelTypeEnum.getEnumByModelName(modelType);
        if (modelTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的AI模型");
        }
        // 5. 通过校验后，添加用户消息到对话历史
        long userMessageId = chatHistoryService.saveUserMessage(appId, message, loginUser);
        // 6. 调用 AI 生成代码
        Flux<String> codeStream = aiCodeGeneratorFacade.generateAndSaveCodeStream(message, codeGenTypeEnum, modelTypeEnum, appId, userMessageId);
        return streamHandlerExecutor.doExecute(codeStream, chatHistoryService, appId, userMessageId, loginUser, codeGenTypeEnum);
    }

    @Override
    public String deployApp(Long appId, String codeDir, User loginUser) {
        // 1. 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "用户未登录");
        // 2. 查询应用信息
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        // 3. 验证用户是否有权限部署该应用，仅本人可以部署
        checkAppAuth(app, loginUser, "无权限部署该应用");
        // 4. 检查是否已有 deployKey
        String deployKey = app.getDeployKey();
        // 没有则生成 6 位 deployKey（大小写字母 + 数字）
        if (StrUtil.isBlank(deployKey)) {
            deployKey = RandomUtil.randomString(6);
        }
        // 5. 获取代码生成类型，构建源目录路径
        String codeGenType = app.getCodeGenType();
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenType);
        String deployCodeDir = codeDir;
        if (codeGenTypeEnum == CodeGenTypeEnum.VUE_PROJECT) {
            if (StrUtil.isBlank(deployCodeDir)) {
                deployCodeDir = appVersionService.getLatestCodeDir(appId);
            }
            ThrowUtils.throwIf(StrUtil.isBlank(deployCodeDir), ErrorCode.SYSTEM_ERROR, "暂无可部署版本");
            appVersionService.updateDeployStatus(appId, deployCodeDir, VersionDeployStatusEnum.DEPLOYING);
        }
        String sourceDirName = codeGenTypeEnum == CodeGenTypeEnum.VUE_PROJECT
                ? vueProjectVersionManager.getVersionDirName(appId, deployCodeDir)
                : codeGenType + "_" + appId + "_" + deployCodeDir;
        String sourceDirPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + sourceDirName ;
        // 6. 检查源目录是否存在
        File sourceDir = new File(sourceDirPath);
        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            markDeployFailed(appId, deployCodeDir, codeGenTypeEnum);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "应用代码不存在，请先生成代码！");
        }
        try {
            // 7. Vue 项目特殊处理：执行构建
            if (codeGenTypeEnum == CodeGenTypeEnum.VUE_PROJECT) {
                boolean buildSuccess = vueProjectBuilder.buildProject(sourceDirPath);
                ThrowUtils.throwIf(!buildSuccess, ErrorCode.SYSTEM_ERROR, "Vue项目构建失败，请检查代码和依赖");
                File distDir = new File(sourceDirPath, "dist");
                ThrowUtils.throwIf(!distDir.exists(), ErrorCode.SYSTEM_ERROR, "Vue 项目构建完成但未生成 dist 目录");
                sourceDir = distDir;
                log.info("Vue 项目构建成功，将部署 dist 目录: {}", distDir.getAbsolutePath());
            }
            // 8. 复制文件到部署目录
            String deployDirPath = AppConstant.CODE_DEPLOY_ROOT_DIR + File.separator + deployKey;
            FileUtil.copyContent(sourceDir, new File(deployDirPath), true);
            // 9. 更新应用的 deployKey 和部署时间
            App updateApp = new App();
            updateApp.setId(appId);
            updateApp.setDeployKey(deployKey);
            updateApp.setDeployedTime(LocalDateTime.now());
            boolean updateResult = this.updateById(updateApp);
            ThrowUtils.throwIf(!updateResult, ErrorCode.OPERATION_ERROR, "更新应用部署信息失败");
            if (codeGenTypeEnum == CodeGenTypeEnum.VUE_PROJECT) {
                appVersionService.updateDeployStatus(appId, deployCodeDir, VersionDeployStatusEnum.SUCCESS);
            }
            // 10. 构建应用访问URL
            String appDeployUrl = String.format("%s/%s/", AppConstant.CODE_DEPLOY_HOST, deployKey);
            // 11. 异步生成截图并更新应用封面
            generateAppScreenshotAsync(appId, appDeployUrl);
            return String.format("%s/%s/", AppConstant.CODE_DEPLOY_HOST, deployKey);
        } catch (Exception e) {
            markDeployFailed(appId, deployCodeDir, codeGenTypeEnum);
            if (e instanceof BusinessException businessException) {
                throw businessException;
            }
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "部署失败：" + e.getMessage());
        }
    }

    /**
     * 异步生成应用截图并更新封面
     *
     * @param appId  应用ID
     * @param appDeployUrl 应用访问URL
     */
    private void generateAppScreenshotAsync(Long appId, String appDeployUrl) {
        // 使用虚拟线程异步执行
        Thread.startVirtualThread(() -> {
            // 调用截图服务生成截图并上传
            String screenshotUrl = screenshotService.generateAndUploadScreenshot(appDeployUrl);
            // 更新应用封面字段
            App updateApp = new App();
            updateApp.setId(appId);
            updateApp.setCover(screenshotUrl);
            boolean updated = this.updateById(updateApp);
            ThrowUtils.throwIf(!updated, ErrorCode.OPERATION_ERROR, "更新应用封面字段失败");
        });
    }

    private void markDeployFailed(Long appId, String codeDir, CodeGenTypeEnum codeGenTypeEnum) {
        if (codeGenTypeEnum == CodeGenTypeEnum.VUE_PROJECT && StrUtil.isNotBlank(codeDir)) {
            appVersionService.updateDeployStatus(appId, codeDir, VersionDeployStatusEnum.FAILED);
        }
    }

    /**
     * 将应用分页结果转换为 VO 分页结果
     *
     * @param appPage   应用实体分页
     * @param pageNum   当前页号
     * @param pageSize  每页大小
     * @param loginUser 当前登录用户（用于 initPrompt 脱敏）
     * @return 应用 VO 分页结果
     */
    private Page<AppVO> toAppVOPage(Page<App> appPage, int pageNum, int pageSize, User loginUser) {
        Page<AppVO> appVOPage = new Page<>(pageNum, pageSize, appPage.getTotalRow());
        if (CollUtil.isEmpty(appPage.getRecords())) {
            return appVOPage;
        }
        // 批量获取用户信息，避免一个一个的查
        Map<Long, UserVO> userVOMap = buildUserVOMap(
                appPage.getRecords().stream().map(App::getUserId).collect(Collectors.toSet()));
        appVOPage.setRecords(appPage.getRecords().stream()
                .map(app -> {
                    AppVO appVO = getAppVO(app);
                    UserVO userVO = userVOMap.get(app.getUserId());
                    appVO.setUser(userVO);
                    return appVO;
                })
                .collect(Collectors.toList()));
        return appVOPage;
    }

    private Map<Long, UserVO> buildUserVOMap(Set<Long> userIds) {
        if (CollUtil.isEmpty(userIds)) {
            return Collections.emptyMap();
        }
        userIds.removeIf(Objects::isNull);
        if (CollUtil.isEmpty(userIds)) {
            return Collections.emptyMap();
        }
        return userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, userService::getUserVO));
    }

    private void validateIsPublish(Integer isPublish) {
        ThrowUtils.throwIf(!AppConstant.APP_NOT_PUBLISH.equals(isPublish)
                        && !APP_PUBLISHED.equals(isPublish),
                ErrorCode.PARAMS_ERROR, "是否公布参数无效");
    }

    /**
     * 校验应用类型是否合法
     */
    private void validateAppTypes(List<String> appTypes) {
        if (CollUtil.isEmpty(appTypes)) {
            return;
        }
        for (String appType : appTypes) {
            ThrowUtils.throwIf(StrUtil.isBlank(appType), ErrorCode.PARAMS_ERROR, "应用类型不能为空");
            ThrowUtils.throwIf(AppTypeEnum.getEnumByValue(appType) == null,
                    ErrorCode.PARAMS_ERROR, "不存在应用类型");
        }
    }

    /**
     * 校验当前用户是否为应用创建者
     *
     * @param app       应用实体
     */
    private void deleteAppFiles(App app) {
        String codeGenType = app.getCodeGenType();
        if (StrUtil.isNotBlank(codeGenType)) {
            String sourceDirPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + codeGenType + "_" + app.getId();
            FileUtil.del(sourceDirPath);
        }
        String deployKey = app.getDeployKey();
        if (StrUtil.isNotBlank(deployKey)) {
            String deployDirPath = AppConstant.CODE_DEPLOY_ROOT_DIR + File.separator + deployKey;
            FileUtil.del(deployDirPath);
        }
    }

    private void checkAppAuth(App app, User loginUser, String message) {
        if (StrUtil.isBlank(message)) {
            message = "无权访问该应用";
        }
        ThrowUtils.throwIf(!app.getUserId().equals(loginUser.getId()), ErrorCode.NO_AUTH_ERROR, message);
    }

    /**
     * 判断当前用户是否可查看 initPrompt
     *
     * @param app       应用实体
     * @param loginUser 当前登录用户
     * @return 管理员或创建者返回 true，否则 false
     */
    private boolean canViewInitPrompt(App app, User loginUser) {
        if (loginUser == null) {
            return false;
        }
        if (UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole())) {
            return true;
        }
        return app.getUserId().equals(loginUser.getId());
    }
}

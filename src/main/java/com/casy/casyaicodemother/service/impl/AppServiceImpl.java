package com.casy.casyaicodemother.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.casy.casyaicodemother.constant.AppConstant;
import com.casy.casyaicodemother.constant.UserConstant;
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
import com.casy.casyaicodemother.model.enums.CodeGenTypeEnum;
import com.casy.casyaicodemother.model.vo.app.AppVO;
import com.casy.casyaicodemother.model.vo.user.UserVO;
import com.casy.casyaicodemother.service.AppService;
import com.casy.casyaicodemother.service.UserService;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 应用 服务层实现。
 */
@Service
public class AppServiceImpl extends ServiceImpl<AppMapper, App> implements AppService {

    @Resource
    private UserService userService;

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
        String appName = appAddRequest.getAppName();
        if (StrUtil.isBlank(appName)) {
            // 应用名称暂时为 initPrompt 前 12 位
            appName = initPrompt.substring(0, Math.min(initPrompt.length(), 12));
        }
        app.setAppName(appName);
        boolean result = save(app);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        // TODO 需要添加版本表的记录，包括模型类型，注意添加事务日志
        return app.getId();
    }

    @Override
    public boolean updateApp(AppUpdateRequest appUpdateRequest, User loginUser) {
        ThrowUtils.throwIf(appUpdateRequest == null || appUpdateRequest.getId() == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(StrUtil.isBlank(appUpdateRequest.getAppName()), ErrorCode.PARAMS_ERROR, "应用名称不能为空");
        App oldApp = getAppById(appUpdateRequest.getId());
        checkAppAuth(oldApp, loginUser);
        App app = new App();
        app.setId(appUpdateRequest.getId());
        app.setAppName(appUpdateRequest.getAppName());
        // 设置编辑时间
        app.setEditTime(LocalDateTime.now());
        return updateById(app);
    }

    @Override
    public boolean updateAppByAdmin(AppAdminUpdateRequest appAdminUpdateRequest) {
        ThrowUtils.throwIf(appAdminUpdateRequest == null || appAdminUpdateRequest.getId() == null, ErrorCode.PARAMS_ERROR);
        getAppById(appAdminUpdateRequest.getId());
        App app = new App();
        BeanUtil.copyProperties(appAdminUpdateRequest, app);
        return updateById(app);
    }

    @Override
    public boolean deleteApp(long id, User loginUser) {
        App app = getAppById(id);
        checkAppAuth(app, loginUser);
        return removeById(id);
    }

    @Override
    public boolean deleteAppByAdmin(long id) {
        getAppById(id);
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
        Set<Long> userIds = appList.stream()
                .map(App::getUserId)
                .collect(Collectors.toSet());
        Map<Long, UserVO> userVOMap = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, userService::getUserVO));
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
        int pageSize = Math.min(appQueryRequest.getPageSize(), AppConstant.MAX_PAGE_SIZE);
        String appName = appQueryRequest.getAppName();
        String sortField = appQueryRequest.getSortField();
        String sortOrder = appQueryRequest.getSortOrder();
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("userId", loginUser.getId())
                .like("appName", appName, StrUtil.isNotBlank(appName))
                .orderBy(sortField, "ascend".equals(sortOrder));
        Page<App> appPage = page(Page.of(pageNum, pageSize), queryWrapper);
        return toAppVOPage(appPage, pageNum, pageSize, loginUser);
    }

    @Override
    public Page<AppVO> listGoodAppVOByPage(AppQueryRequest appQueryRequest, User loginUser) {
        ThrowUtils.throwIf(appQueryRequest == null, ErrorCode.PARAMS_ERROR);
        int pageNum = appQueryRequest.getPageNum();
        int pageSize = Math.min(appQueryRequest.getPageSize(), AppConstant.MAX_PAGE_SIZE);
        String appName = appQueryRequest.getAppName();
        String sortField = appQueryRequest.getSortField();
        String sortOrder = appQueryRequest.getSortOrder();
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("priority", AppConstant.GOOD_APP_PRIORITY)
                .like("appName", appName, StrUtil.isNotBlank(appName))
                .orderBy(sortField, "ascend".equals(sortOrder));
        Page<App> appPage = page(Page.of(pageNum, pageSize), queryWrapper);
        return toAppVOPage(appPage, pageNum, pageSize, loginUser);
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
        String deployKey = appQueryRequest.getDeployKey();
        Integer priority = appQueryRequest.getPriority();
        Long userId = appQueryRequest.getUserId();
        String sortField = appQueryRequest.getSortField();
        String sortOrder = appQueryRequest.getSortOrder();
        return QueryWrapper.create()
                .eq("id", id)
                .like("appName", appName, StrUtil.isNotBlank(appName))
                .like("cover", cover, StrUtil.isNotBlank(cover))
                .like("initPrompt", initPrompt, StrUtil.isNotBlank(initPrompt))
                .eq("codeGenType", codeGenType, StrUtil.isNotBlank(codeGenType))
                .eq("deployKey", deployKey, StrUtil.isNotBlank(deployKey))
                .eq("priority", priority, priority != null)
                .eq("userId", userId)
                .orderBy(sortField, "ascend".equals(sortOrder));
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

        // 批量获取用户信息，避免一个一个的查
        Set<Long> userIds = appPage.getRecords().stream()
                .map(App::getUserId)
                .collect(Collectors.toSet());
        Map<Long, UserVO> userVOMap = userService.listByIds(userIds).stream().collect(Collectors.toMap(User::getId, userService::getUserVO));
        appVOPage.setRecords(appPage.getRecords().stream()
                .map(app -> {
                    AppVO appVO = getAppVO(app, loginUser);
                    UserVO userVO = userVOMap.get(app.getUserId());
                    appVO.setUser(userVO);
                    return appVO;
                })
                .collect(Collectors.toList()));
        return appVOPage;
    }

    /**
     * 校验当前用户是否为应用创建者
     *
     * @param app       应用实体
     * @param loginUser 当前登录用户
     */
    private void checkAppAuth(App app, User loginUser) {
        ThrowUtils.throwIf(!app.getUserId().equals(loginUser.getId()), ErrorCode.NO_AUTH_ERROR);
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

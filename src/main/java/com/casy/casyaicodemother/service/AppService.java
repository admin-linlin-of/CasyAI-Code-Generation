package com.casy.casyaicodemother.service;

import com.casy.casyaicodemother.model.dto.app.*;
import com.casy.casyaicodemother.model.entity.App;
import com.casy.casyaicodemother.model.entity.User;
import com.casy.casyaicodemother.model.vo.app.AppVO;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;

import java.util.List;

/**
 * 应用 服务层。
 */
public interface AppService extends IService<App> {

    /**
     * 创建应用
     *
     * @param appAddRequest 创建请求（initPrompt 必填）
     * @param loginUser     当前登录用户
     * @return 新应用 id
     */
    long createApp(AppAddRequest appAddRequest, User loginUser);

    /**
     * 用户更新自己的应用（仅应用名称）
     *
     * @param appUpdateRequest 更新请求
     * @param loginUser        当前登录用户
     * @return 是否更新成功
     */
    boolean updateApp(AppUpdateRequest appUpdateRequest, User loginUser);

    /**
     * 管理员更新任意应用（名称、封面、优先级）
     *
     * @param appAdminUpdateRequest 管理员更新请求
     * @return 是否更新成功
     */
    boolean updateAppByAdmin(AppAdminUpdateRequest appAdminUpdateRequest);

    /**
     * 用户删除自己的应用
     *
     * @param id        应用 id
     * @param loginUser 当前登录用户
     * @return 是否删除成功
     */
    boolean deleteApp(long id, User loginUser);

    /**
     * 管理员删除任意应用
     *
     * @param id 应用 id
     * @return 是否删除成功
     */
    boolean deleteAppByAdmin(long id);

    /**
     * 根据 id 获取应用实体
     *
     * @param id 应用 id
     * @return 应用实体
     */
    App getAppById(long id);

    /**
     * 获取应用 VO（按权限决定是否返回 initPrompt）
     *
     * @param app       应用实体
     * @param loginUser 当前登录用户，可为 null
     * @return 应用 VO
     */
    AppVO getAppVO(App app, User loginUser);

    /**
     * 获取应用 VO（不脱敏 initPrompt）
     *
     * @param app 应用实体
     * @return 应用 VO，app 为 null 时返回 null
     */
    AppVO getAppVO(App app);

    /**
     * 批量转换为应用 VO 列表
     *
     * @param appList 应用实体列表
     * @return 应用 VO 列表
     */
    List<AppVO> getAppVOList(List<App> appList);

    /**
     * 分页查询当前用户的应用列表
     *
     * @param appQueryRequest 分页及查询条件（appName 模糊查询）
     * @param loginUser       当前登录用户
     * @return 应用 VO 分页结果，每页最多 20 条
     */
    Page<AppVO> listMyAppVOByPage(AppQueryRequest appQueryRequest, User loginUser);

    /**
     * 分页查询精选应用列表
     *
     * @param appQueryRequest 分页及查询条件（appName 模糊查询）
     * @param loginUser       当前登录用户（用于 initPrompt 脱敏）
     * @return 应用 VO 分页结果，每页最多 20 条
     */
    Page<AppVO> listGoodAppVOByPage(AppQueryRequest appQueryRequest, User loginUser);

    /**
     * 管理员分页查询应用列表
     *
     * @param appQueryRequest 分页及查询条件
     * @param loginUser       当前登录用户（管理员可见 initPrompt）
     * @return 应用 VO 分页结果
     */
    Page<AppVO> listAppVOByPage(AppQueryRequest appQueryRequest, User loginUser);

    /**
     * 将查询请求转为 QueryWrapper（不含时间字段条件）
     *
     * @param appQueryRequest 查询请求
     * @return MyBatis-Flex 查询条件
     */
    QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest);
}

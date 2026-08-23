package com.casy.casyaicodemother.service;

import com.casy.casyaicodemother.model.dto.app.AppAddRequest;
import com.casy.casyaicodemother.model.dto.app.AppAdminUpdateRequest;
import com.casy.casyaicodemother.model.dto.app.AppQueryRequest;
import com.casy.casyaicodemother.model.dto.app.AppUpdateRequest;
import com.casy.casyaicodemother.model.entity.App;
import com.casy.casyaicodemother.model.entity.User;
import com.casy.casyaicodemother.model.vo.app.AppVO;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.CompletableFuture;

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
     * 用户更新自己的应用（应用名称、类型、是否公布）
     *
     * @param appUpdateRequest 更新请求
     * @param loginUser        当前登录用户
     * @return 是否更新成功
     */
    boolean updateApp(AppUpdateRequest appUpdateRequest, User loginUser);

    /**
     * 管理员更新任意应用（名称、封面、优先级、是否公布）
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
     * @return 应用 VO 分页结果，每页最多 20 条
     */
    Page<AppVO> listGoodAppVOByPage(AppQueryRequest appQueryRequest);

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

    /**
     * 根据用户的提示词去调用AI并生成代码
     *
     * @param appId 应用ID
     * @param message 用户提示词
     * @param modelType 模型类型
     * @param loginUser 登录用户
     * @param versionDir 版本目录，于用在修改时指定目录
     * @param agent true 走工作流，false 走传统生成
     * @return AI响应流
     */
    Flux<String> chatToGenCode(Long appId, String message, String modelType, User loginUser, String versionDir, Boolean agent);

    /**
     * 后台截网页并更新应用封面，不阻塞调用方。
     * 工作流在代码落盘/Vue 打包后提交；部署成功后也会走同一套逻辑。
     *
     * @param appId  应用 ID
     * @param webUrl 可供无头 Chrome 打开的地址（本机 static 预览或部署域名）
     * @return 封面图 URL；失败为 null
     */
    CompletableFuture<String> generateAppCoverAsync(Long appId, String webUrl);

    /**
     * 部署应用
     *
     * @param appId 应用id
     * @param loginUser 登录用户
     * @return 部署网址
     */
    String deployApp(Long appId, String codeDir, User loginUser);
}

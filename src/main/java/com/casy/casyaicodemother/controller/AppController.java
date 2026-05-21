package com.casy.casyaicodemother.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.casy.casyaicodemother.common.BaseResponse;
import com.casy.casyaicodemother.common.DeleteRequest;
import com.casy.casyaicodemother.common.ResultUtils;
import com.casy.casyaicodemother.constant.UserConstant;
import com.casy.casyaicodemother.exception.BusinessException;
import com.casy.casyaicodemother.exception.ErrorCode;
import com.casy.casyaicodemother.exception.ThrowUtils;
import com.casy.casyaicodemother.model.dto.app.*;
import com.casy.casyaicodemother.model.entity.App;
import com.casy.casyaicodemother.model.entity.User;
import com.casy.casyaicodemother.model.vo.app.AppVO;
import com.casy.casyaicodemother.service.AppService;
import com.casy.casyaicodemother.service.UserService;
import com.mybatisflex.core.paginate.Page;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 应用 控制层。
 */
@Slf4j
@RestController
@RequestMapping("/app")
public class AppController {

    @Resource
    private AppService appService;

    @Resource
    private UserService userService;

    /**
     * 创建应用（须填写 initPrompt）
     *
     * @param appAddRequest 创建应用请求
     * @return 新应用 id
     */
    @SaCheckLogin
    @PostMapping("/add")
    public BaseResponse<Long> addApp(@RequestBody AppAddRequest appAddRequest) {
        ThrowUtils.throwIf(appAddRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser();
        long appId = appService.createApp(appAddRequest, loginUser);
        return ResultUtils.success(appId);
    }

    /**
     * 根据 id 修改自己的应用（仅支持修改应用名称，并且用户只能更新自己的应用名称）
     *
     * @param appUpdateRequest 更新应用请求
     * @return 是否更新成功
     */
    @SaCheckLogin
    @PostMapping("/update")
    public BaseResponse<Boolean> updateApp(@RequestBody AppUpdateRequest appUpdateRequest) {
        ThrowUtils.throwIf(appUpdateRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser();
        boolean result = appService.updateApp(appUpdateRequest, loginUser);
        return ResultUtils.success(result);
    }

    /**
     * 根据 id 删除应用（普通用户仅能删除自己的应用，管理员可删除任意应用）
     *
     * @param deleteRequest 删除请求（含应用 id）
     * @return 是否删除成功
     */
    @SaCheckLogin
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteApp(@RequestBody DeleteRequest deleteRequest) {
        if (deleteRequest == null || deleteRequest.getId() == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser();
        boolean result;
        if (UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole())) {
            result = appService.deleteAppByAdmin(deleteRequest.getId());
        } else {
            result = appService.deleteApp(deleteRequest.getId(), loginUser);
        }
        return ResultUtils.success(result);
    }

    /**
     * 根据 id 查看应用详情（非本人/非管理员不返回 initPrompt）
     *
     * @param id 应用 id
     * @return 应用详情 VO
     */
    @SaCheckLogin
    @GetMapping("/get/vo")
    public BaseResponse<AppVO> getAppVOById(long id) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser();
        App app = appService.getAppById(id);
        return ResultUtils.success(appService.getAppVO(app, loginUser));
    }

    /**
     * 分页查询自己的应用列表（支持按名称模糊查询，每页最多 20 条）
     *
     * @param appQueryRequest 分页及查询条件
     * @return 应用 VO 分页结果
     */
    @SaCheckLogin
    @PostMapping("/list/my/page/vo")
    public BaseResponse<Page<AppVO>> listMyAppVOByPage(@RequestBody AppQueryRequest appQueryRequest) {
        ThrowUtils.throwIf(appQueryRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser();
        Page<AppVO> appVOPage = appService.listMyAppVOByPage(appQueryRequest, loginUser);
        return ResultUtils.success(appVOPage);
    }

    /**
     * 分页查询精选应用列表（priority=99，支持按名称模糊查询，每页最多 20 条）
     *
     * @param appQueryRequest 分页及查询条件
     * @return 应用 VO 分页结果
     */
    @SaCheckLogin
    @PostMapping("/list/good/page/vo")
    public BaseResponse<Page<AppVO>> listGoodAppVOByPage(@RequestBody AppQueryRequest appQueryRequest) {
        ThrowUtils.throwIf(appQueryRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser();
        Page<AppVO> appVOPage = appService.listGoodAppVOByPage(appQueryRequest, loginUser);
        return ResultUtils.success(appVOPage);
    }

    /**
     * 管理员根据 id 更新任意应用（支持更新应用名称、封面、优先级）
     *
     * @param appAdminUpdateRequest 管理员更新请求
     * @return 是否更新成功
     */
    @PostMapping("/admin/update")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateAppByAdmin(@RequestBody AppAdminUpdateRequest appAdminUpdateRequest) {
        ThrowUtils.throwIf(appAdminUpdateRequest == null, ErrorCode.PARAMS_ERROR);
        boolean result = appService.updateAppByAdmin(appAdminUpdateRequest);
        return ResultUtils.success(result);
    }

    /**
     * 管理员分页查询应用列表（支持按除时间外的字段查询，每页数量不限）
     *
     * @param appQueryRequest 分页及查询条件
     * @return 应用 VO 分页结果
     */
    @PostMapping("/list/page/vo")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<AppVO>> listAppVOByPage(@RequestBody AppQueryRequest appQueryRequest) {
        ThrowUtils.throwIf(appQueryRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser();
        Page<AppVO> appVOPage = appService.listAppVOByPage(appQueryRequest, loginUser);
        return ResultUtils.success(appVOPage);
    }

    /**
     * 管理员根据 id 查看应用详情（返回完整实体）
     *
     * @param id 应用 id
     * @return 应用实体
     */
    @GetMapping("/get")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<App> getAppById(long id) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        App app = appService.getAppById(id);
        return ResultUtils.success(app);
    }

    /**
     * 应用聊天生成代码（流式 SSE）
     * 1. 前端使用 EventSource 对接目前的接口时，会出现空格丢失问题，将内容包装成JSON对象
     * 2. 发送结束事件
     * 在 SSE 中，当服务器关闭连接时，会触发客户端的 onclose 事件，这是前端判断流结束的标准方式。
     * 但是，onclose事件会在连接正常结束（服务器主动关闭）和异常中断（如网络问题）时都触发，前端就很难区分到底后端是正常响应了所有数据、还是异常中断了。
     * 因此，我们最好在后端添加一个明确的 done 事件，这样可以更清晰地区分流的正常结束和异常中断。
     *
     * @param appId     应用 ID
     * @param message   用户消息
     * @param modelType 模型类型
     * @return 生成结果流
     */
    @GetMapping(value = "/chat/gen/code", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatToGenCode(@RequestParam Long appId,
                                                       @RequestParam String message,
                                                       @RequestParam String modelType) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID无效");
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "用户消息不能为空");
        User loginUser = userService.getLoginUser();
        //前端使用 EventSource 对接目前的接口时，会出现空格丢失问题。
        return appService.chatToGenCode(appId, message, modelType, loginUser)
                .map(chunk -> {
                    // 前端使用 EventSource 对接目前的接口时，会出现空格丢失问题，将内容包装成JSON对象
                    Map<String, String> wrapper = Map.of("c", chunk);
                    String jsonData = JSONUtil.toJsonStr(wrapper);
                    return ServerSentEvent.<String>builder()
                            .data(jsonData)
                            .build();
                })
                .concatWith(Mono.just(
                        ServerSentEvent.<String>builder()
                                .event("done").data("").build()
                ));
    }

    /**
     * 应用部署
     *
     * @param appDeployRequest 部署请求
     * @return 部署 URL
     */
    @PostMapping("/deploy")
    public BaseResponse<String> deployApp(@RequestBody AppDeployRequest appDeployRequest) {
        ThrowUtils.throwIf(appDeployRequest == null, ErrorCode.PARAMS_ERROR);
        Long appId = appDeployRequest.getAppId();
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        // 获取当前登录用户
        User loginUser = userService.getLoginUser();
        // 调用服务部署应用
        String deployUrl = appService.deployApp(appId, loginUser);
        return ResultUtils.success(deployUrl);
    }

}

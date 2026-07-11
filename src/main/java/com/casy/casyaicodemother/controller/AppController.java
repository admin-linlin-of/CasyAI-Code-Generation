package com.casy.casyaicodemother.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaIgnore;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.casy.casyaicodemother.common.BaseResponse;
import com.casy.casyaicodemother.common.DeleteRequest;
import com.casy.casyaicodemother.common.ResultUtils;
import com.casy.casyaicodemother.constant.AppConstant;
import com.casy.casyaicodemother.constant.UserConstant;
import com.casy.casyaicodemother.exception.BusinessException;
import com.casy.casyaicodemother.exception.ErrorCode;
import com.casy.casyaicodemother.exception.ThrowUtils;
import com.casy.casyaicodemother.model.dto.app.*;
import com.casy.casyaicodemother.model.entity.App;
import com.casy.casyaicodemother.model.entity.User;
import com.casy.casyaicodemother.model.vo.app.AppCodeFileListVO;
import com.casy.casyaicodemother.model.vo.app.AppVO;
import com.casy.casyaicodemother.service.AppCodeFileService;
import com.casy.casyaicodemother.service.AppService;
import com.casy.casyaicodemother.service.ProjectDownloadService;
import com.casy.casyaicodemother.service.UserService;
import com.mybatisflex.core.paginate.Page;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.File;
import java.util.List;
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

    @Resource
    private ProjectDownloadService projectDownloadService;

    @Resource
    private AppCodeFileService appCodeFileService;

    /**
     * 创建应用（须填写 initPrompt）
     *
     * @param appAddRequest 创建应用请求
     * @return 新应用 id
     */
    @Operation(summary = "创建应用")
    @SaCheckLogin
    @PostMapping("/add")
    public BaseResponse<Long> addApp(@RequestBody AppAddRequest appAddRequest) {
        ThrowUtils.throwIf(appAddRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser();
        long appId = appService.createApp(appAddRequest, loginUser);
        return ResultUtils.success(appId);
    }

    /**
     * 根据 id 修改自己的应用（支持修改应用名称、类型、是否公布）
     *
     * @param appUpdateRequest 更新应用请求
     * @return 是否更新成功
     */
    @Operation(summary = "更新应用")
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
    @Operation(summary = "删除应用")
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
    @Operation(summary = "查看应用详情")
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
    @Operation(summary = "分页查询自己的应用")
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
    @Operation(summary = "分页查询精选应用")
    @SaIgnore
    @PostMapping("/list/good/page/vo")
    public BaseResponse<Page<AppVO>> listGoodAppVOByPage(@RequestBody AppQueryRequest appQueryRequest) {
        ThrowUtils.throwIf(appQueryRequest == null, ErrorCode.PARAMS_ERROR);
        Page<AppVO> appVOPage = appService.listGoodAppVOByPage(appQueryRequest);
        return ResultUtils.success(appVOPage);
    }

    /**
     * 管理员根据 id 更新任意应用（支持更新应用名称、封面、优先级、是否公布）
     *
     * @param appAdminUpdateRequest 管理员更新请求
     * @return 是否更新成功
     */
    @Operation(summary = "管理员更新应用")
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
    @Operation(summary = "管理员分页查询应用")
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
    @Operation(summary = "管理员查看应用详情")
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
    @Operation(summary = "应用聊天生成代码")
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
                    // 深度思考已由 JsonMessageStreamHandler 包装为 {"c":"...","t":"thinking"}，直接透传
                    String jsonData;
                    if (chunk.startsWith("{") && chunk.contains("\"t\"")) {
                        jsonData = chunk;
                    } else {
                        // 普通内容包装为 {"c":"..."}，避免 EventSource 丢空格
                        jsonData = JSONUtil.toJsonStr(Map.of("c", chunk));
                    }
                    return ServerSentEvent.<String>builder()
                            .data(jsonData)
                            .build();
                })
                .concatWith(Mono.just(
                        ServerSentEvent.<String>builder()
                                .event("done").data("").build()
                ))
                // SSE 接口不能走 GlobalExceptionHandler（返回 JSON），需在流内将异常转为 SSE 数据推送给前端
                .onErrorResume(e -> {
                    log.error("chatToGenCode stream error", e);
                    String msg = StrUtil.blankToDefault(e.getMessage(), "未知错误");
                    if (!msg.startsWith("生成失败")) {
                        msg = "生成失败：" + msg;
                    }
                    String jsonData = JSONUtil.toJsonStr(Map.of("c", msg));
                    return Flux.just(
                            ServerSentEvent.<String>builder().data(jsonData).build(),
                            ServerSentEvent.<String>builder().event("done").data("").build()
                    );
                });
    }

    /**
     * 列出 Vue 项目版本目录下的源码文件相对路径（供前端代码预览文件树使用）。
     */
    @Operation(summary = "列出 Vue 项目源码文件")
    @SaCheckLogin
    @GetMapping("/code/files")
    public BaseResponse<AppCodeFileListVO> listCodeFiles(@RequestParam Long appId,
                                                         @RequestParam String codeDir) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID无效");
        ThrowUtils.throwIf(StrUtil.isBlank(codeDir), ErrorCode.PARAMS_ERROR, "版本目录不能为空");
        User loginUser = userService.getLoginUser();
        List<String> files = appCodeFileService.listVueProjectFiles(appId, codeDir, loginUser);
        AppCodeFileListVO vo = new AppCodeFileListVO();
        vo.setFiles(files);
        return ResultUtils.success(vo);
    }

    /**
     * 应用部署
     *
     * @param appDeployRequest 部署请求
     * @return 部署 URL
     */
    @Operation(summary = "应用部署")
    @PostMapping("/deploy")
    public BaseResponse<String> deployApp(@RequestBody AppDeployRequest appDeployRequest) {
        ThrowUtils.throwIf(appDeployRequest == null, ErrorCode.PARAMS_ERROR);
        Long appId = appDeployRequest.getAppId();
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        // 获取当前登录用户
        User loginUser = userService.getLoginUser();
        // 调用服务部署应用
        String deployUrl = appService.deployApp(appId, appDeployRequest.getCodeDir(), loginUser);
        return ResultUtils.success(deployUrl);
    }


    /**
     * 下载应用代码
     *
     * @param appId    应用ID
     * @param version  版本号比如（v1)
     * @param response 响应
     */
    @GetMapping("/download/{appId}/{version}")
    public void downloadAppCode(@PathVariable Long appId,
                                @PathVariable String version,
                                HttpServletResponse response) {
        // 1. 基础校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID无效");
        // 2. 查询应用信息
        App app = appService.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        // 3. 权限校验：只有应用创建者可以下载代码
        User loginUser = userService.getLoginUser();
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限下载该应用代码");
        }
        // 4. 构建应用代码目录路径（生成目录，非部署目录）
        String codeGenType = app.getCodeGenType();
        String sourceDirName = codeGenType + "_" + appId;
        String sourceDirPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + sourceDirName + "_" + version;
        // 5. 检查代码目录是否存在
        File sourceDir = new File(sourceDirPath);
        ThrowUtils.throwIf(!sourceDir.exists() || !sourceDir.isDirectory(),
                ErrorCode.NOT_FOUND_ERROR, "应用代码不存在，请先生成代码");
        // 6. 生成下载文件名（不建议添加中文内容）
        String downloadFileName = String.valueOf(appId);
        // 7. 调用通用下载服务
        projectDownloadService.downloadProjectAsZip(sourceDirPath, downloadFileName, response);
    }
}

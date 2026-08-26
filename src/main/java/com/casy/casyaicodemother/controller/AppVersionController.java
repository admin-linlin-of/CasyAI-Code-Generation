package com.casy.casyaicodemother.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.hutool.core.util.StrUtil;
import com.casy.casyaicodemother.common.BaseResponse;
import com.casy.casyaicodemother.common.ResultUtils;
import com.casy.casyaicodemother.constant.UserConstant;
import com.casy.casyaicodemother.exception.ErrorCode;
import com.casy.casyaicodemother.exception.ThrowUtils;
import com.casy.casyaicodemother.model.dto.app.AppVersionRequest;
import com.casy.casyaicodemother.model.dto.app.AppVersionRetryBuildRequest;
import com.casy.casyaicodemother.model.entity.AppVersion;
import com.casy.casyaicodemother.model.entity.User;
import com.casy.casyaicodemother.service.AppVersionService;
import com.casy.casyaicodemother.service.UserService;
import com.mybatisflex.core.paginate.Page;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/tAppVersion")
public class AppVersionController {

    @Resource
    private AppVersionService tAppVersionService;

    @Resource
    private UserService userService;

    @Operation(summary = "根据应用ID查询代码版本")
    @SaCheckLogin
    @GetMapping("list/{appid}")
    public List<AppVersion> getAppVersionsByAppId(@PathVariable Long appid) {
        User loginUser = userService.getLoginUser();
        return tAppVersionService.getAppVersionsByAppId(appid, loginUser);
    }

    @Operation(summary = "重新打包指定版本")
    @SaCheckLogin
    @PostMapping("/retryBuild")
    public BaseResponse<Boolean> retryBuild(@RequestBody AppVersionRetryBuildRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser();
        tAppVersionService.retryBuild(Long.valueOf(request.getAppId()), request.getCodeDir(), loginUser);
        return ResultUtils.success(true);
    }

    @Operation(summary = "打包指定版本")
    @SaCheckLogin
    @PostMapping("/build")
    public BaseResponse<Boolean> buildVersion(@RequestBody AppVersionRetryBuildRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser();
        Long appId = Long.valueOf(StrUtil.isNotBlank(request.getAppId()) ? request.getAppId() : "0");
        tAppVersionService.buildVersion(appId, request.getCodeDir(), loginUser);
        return ResultUtils.success(true);
    }

    /**
     * SSE 订阅 Vue 版本打包进度（传统模式替代轮询）。
     * <p>
     * 一次请求完成「触发异步 build + 推送状态」：
     * <ul>
     *   <li>{@code event=build_status, data={"status":"building|success|failed"}}</li>
     *   <li>终态后 {@code event=done} 并关闭连接</li>
     * </ul>
     *
     * @param skipIfSuccess 工作流传 true：若工作流节点已同步 build，直接返回当前状态、不重复打包
     */
    @Operation(summary = "SSE 订阅 Vue 版本打包状态（触发打包并推送 building/success/failed）")
    @SaCheckLogin
    @GetMapping(value = "/build/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter buildVersionStream(@RequestParam Long appId,
                                         @RequestParam String codeDir,
                                         @RequestParam(required = false, defaultValue = "false") Boolean skipIfSuccess) {
        User loginUser = userService.getLoginUser();
        return tAppVersionService.buildVersionStream(appId, codeDir, loginUser, Boolean.TRUE.equals(skipIfSuccess));
    }

    @Operation(summary = "管理员分页查询应用代码版本")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    @PostMapping("/list/page")
    public BaseResponse<Page<AppVersion>> listAppVersionByPage(@RequestBody AppVersionRequest appVersionRequest) {
        ThrowUtils.throwIf(appVersionRequest == null, ErrorCode.PARAMS_ERROR);
        Page<AppVersion> appVersionPage = tAppVersionService.listAppVersionByPage(appVersionRequest);
        return ResultUtils.success(appVersionPage);
    }

    @Operation(summary = "保存应用代码版本")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    @PostMapping("save")
    public boolean save(@RequestBody AppVersion appVersion) {
        return tAppVersionService.save(appVersion);
    }

    @Operation(summary = "根据主键删除应用代码版本")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    @DeleteMapping("remove/{id}")
    public boolean remove(@PathVariable Long id) {
        AppVersion appVersion = tAppVersionService.getById(id);
        ThrowUtils.throwIf(appVersion == null, ErrorCode.NOT_FOUND_ERROR);
        tAppVersionService.removeByAppVersion(appVersion);
        return true;
    }

    @Operation(summary = "根据主键更新应用代码版本")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    @PutMapping("update")
    public boolean update(@RequestBody AppVersion appVersion) {
        return tAppVersionService.updateById(appVersion);
    }

    @Operation(summary = "查询所有应用代码版本")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    @GetMapping("list")
    public List<AppVersion> list() {
        return tAppVersionService.list();
    }

    @Operation(summary = "根据主键获取应用代码版本")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    @GetMapping("getInfo/{id}")
    public AppVersion getInfo(@PathVariable Long id) {
        return tAppVersionService.getById(id);
    }

    @Operation(summary = "分页查询应用代码版本")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    @GetMapping("page")
    public Page<AppVersion> page(Page<AppVersion> page) {
        return tAppVersionService.page(page);
    }

}

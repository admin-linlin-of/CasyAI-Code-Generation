package com.casy.casyaicodemother.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.casy.casyaicodemother.common.BaseResponse;
import com.casy.casyaicodemother.common.ResultUtils;
import com.casy.casyaicodemother.constant.UserConstant;
import com.casy.casyaicodemother.exception.ErrorCode;
import com.casy.casyaicodemother.exception.ThrowUtils;
import com.casy.casyaicodemother.model.dto.app.AppVersionRequest;
import com.casy.casyaicodemother.model.entity.AppVersion;
import com.casy.casyaicodemother.model.entity.User;
import com.casy.casyaicodemother.service.AppVersionService;
import com.casy.casyaicodemother.service.UserService;
import com.mybatisflex.core.paginate.Page;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

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

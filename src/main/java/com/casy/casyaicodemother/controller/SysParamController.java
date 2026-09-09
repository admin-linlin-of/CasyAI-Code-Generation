package com.casy.casyaicodemother.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.casy.casyaicodemother.common.BaseResponse;
import com.casy.casyaicodemother.common.DeleteRequest;
import com.casy.casyaicodemother.common.ResultUtils;
import com.casy.casyaicodemother.constant.UserConstant;
import com.casy.casyaicodemother.exception.ErrorCode;
import com.casy.casyaicodemother.exception.ThrowUtils;
import com.casy.casyaicodemother.model.dto.sysparam.SysParamAddRequest;
import com.casy.casyaicodemother.model.dto.sysparam.SysParamUpdateRequest;
import com.casy.casyaicodemother.model.entity.SysParam;
import com.casy.casyaicodemother.service.SysParamService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/sysParam")
public class SysParamController {

    @Resource
    private SysParamService sysParamService;

    @Operation(summary = "公开参数（未登录可访问，供首页图标等使用）")
    @GetMapping("/public")
    public BaseResponse<Map<String, String>> publicValues() {
        return ResultUtils.success(sysParamService.listPublicValues());
    }

    @Operation(summary = "查询全部系统参数")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    @GetMapping("/list")
    public BaseResponse<List<SysParam>> list() {
        return ResultUtils.success(sysParamService.listAll());
    }

    @Operation(summary = "新增系统参数")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    @PostMapping("/add")
    public BaseResponse<Long> add(@RequestBody SysParamAddRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(sysParamService.addParam(request));
    }

    @Operation(summary = "更新系统参数")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    @PostMapping("/update")
    public BaseResponse<Boolean> update(@RequestBody SysParamUpdateRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(sysParamService.updateParam(request));
    }

    @Operation(summary = "删除系统参数")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    @PostMapping("/delete")
    public BaseResponse<Boolean> delete(@RequestBody DeleteRequest request) {
        ThrowUtils.throwIf(request == null || request.getId() == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(sysParamService.deleteParam(request.getId()));
    }
}

package com.casy.casyaicodemother.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.casy.casyaicodemother.common.BaseResponse;
import com.casy.casyaicodemother.common.ResultUtils;
import com.casy.casyaicodemother.constant.UserConstant;
import com.casy.casyaicodemother.exception.ErrorCode;
import com.casy.casyaicodemother.exception.ThrowUtils;
import com.casy.casyaicodemother.model.dto.aimodel.AiModelEnabledUpdateRequest;
import com.casy.casyaicodemother.model.entity.AiModel;
import com.casy.casyaicodemother.service.AiModelCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 模型目录查询与开关；改表后下次路由即生效 */
@RestController
@RequestMapping("/aiModel")
public class AiModelController {

    @Resource
    private AiModelCatalogService aiModelCatalogService;

    @Operation(summary = "查询全部模型（含停用）")
    @SaCheckLogin
    @GetMapping("/list")
    public BaseResponse<List<AiModel>> list() {
        return ResultUtils.success(aiModelCatalogService.listAll());
    }

    @Operation(summary = "查询当前可用模型")
    @SaCheckLogin
    @GetMapping("/enabled")
    public BaseResponse<List<AiModel>> enabled() {
        return ResultUtils.success(aiModelCatalogService.listEnabled());
    }

    @Operation(summary = "启用或停用模型")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    @PostMapping("/enabled")
    public BaseResponse<Boolean> updateEnabled(@RequestBody AiModelEnabledUpdateRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(aiModelCatalogService.updateEnabled(request.getId(), request.getEnabled()));
    }
}

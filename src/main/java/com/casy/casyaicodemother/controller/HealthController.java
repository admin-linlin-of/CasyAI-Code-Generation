package com.casy.casyaicodemother.controller;

import com.casy.casyaicodemother.common.BaseResponse;
import com.casy.casyaicodemother.common.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/health")
public class HealthController {

    @Operation(summary = "健康检查")
    @GetMapping("/")
    public BaseResponse<String> healthCheck() {
        return ResultUtils.success( "ok");
    }
}

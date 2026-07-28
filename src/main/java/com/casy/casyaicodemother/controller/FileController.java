package com.casy.casyaicodemother.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.casy.casyaicodemother.common.BaseResponse;
import com.casy.casyaicodemother.common.ResultUtils;
import com.casy.casyaicodemother.service.FileUploadService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 通用文件上传接口（聊天粘贴图片等）。
 */
@RestController
@RequestMapping("/file")
public class FileController {

    @Resource
    private FileUploadService fileUploadService;

    /**
     * 上传图片到对象存储，返回可访问 URL。
     * <p>
     * 前端：FormData 字段名必须为 {@code file}。
     *
     * @param file 图片文件（png/jpeg/gif/webp，≤5MB）
     * @return OSS 访问地址
     */
    @Operation(summary = "上传图片")
    @SaCheckLogin
    @PostMapping("/upload")
    public BaseResponse<String> uploadImage(@RequestParam("file") MultipartFile file) {
        String url = fileUploadService.uploadImage(file);
        return ResultUtils.success(url);
    }
}

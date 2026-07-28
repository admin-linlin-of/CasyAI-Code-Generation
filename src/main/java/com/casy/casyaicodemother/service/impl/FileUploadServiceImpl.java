package com.casy.casyaicodemother.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.casy.casyaicodemother.exception.BusinessException;
import com.casy.casyaicodemother.exception.ErrorCode;
import com.casy.casyaicodemother.exception.ThrowUtils;
import com.casy.casyaicodemother.manager.oss.OssManager;
import com.casy.casyaicodemother.service.FileUploadService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;

/**
 * 聊天图片等文件上传实现：校验 → 落临时文件 → OssManager 上传 → 清理临时文件。
 */
@Slf4j
@Service
public class FileUploadServiceImpl implements FileUploadService {

    /** 允许的图片 MIME 类型 */
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/png", "image/jpeg", "image/jpg", "image/gif", "image/webp"
    );

    /** 单文件上限 5MB */
    private static final long MAX_SIZE = 5 * 1024 * 1024L;

    @Resource
    private OssManager ossManager;

    @Override
    public String uploadImage(MultipartFile file) {
        // 1. 基础校验：非空、类型、大小
        ThrowUtils.throwIf(file == null || file.isEmpty(), ErrorCode.PARAMS_ERROR, "上传文件不能为空");
        String contentType = file.getContentType();
        ThrowUtils.throwIf(
                StrUtil.isBlank(contentType) || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase()),
                ErrorCode.PARAMS_ERROR,
                "仅支持 png/jpeg/gif/webp 图片"
        );
        ThrowUtils.throwIf(file.getSize() > MAX_SIZE, ErrorCode.PARAMS_ERROR, "图片大小不能超过 5MB");

        // 2. 转存临时文件（OssManager 需要 java.io.File）
        String ext = resolveExtension(file.getOriginalFilename(), contentType);
        File tempFile = null;
        try {
            tempFile = File.createTempFile("chat-img-", "." + ext);
            file.transferTo(tempFile);

            // 3. 生成对象键并上传到 OSS
            String key = buildObjectKey(ext);
            String url = ossManager.uploadFile(key, tempFile);
            ThrowUtils.throwIf(StrUtil.isBlank(url), ErrorCode.OPERATION_ERROR, "图片上传失败");
            log.info("聊天图片上传成功: original={}, url={}", file.getOriginalFilename(), url);
            return url;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("聊天图片上传异常", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "图片上传失败");
        } finally {
            // 4. 无论成功失败都清理临时文件
            if (tempFile != null && tempFile.exists()) {
                FileUtil.del(tempFile);
            }
        }
    }

    /**
     * 对象键：chat-images/yyyy/MM/dd/{uuid}.{ext}
     */
    private String buildObjectKey(String ext) {
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        return String.format("chat-images/%s/%s.%s", datePath, IdUtil.simpleUUID(), ext);
    }

    /**
     * 优先用原始文件名后缀，否则按 Content-Type 推断。
     */
    private String resolveExtension(String originalFilename, String contentType) {
        String suffix = FileUtil.getSuffix(originalFilename);
        if (StrUtil.isNotBlank(suffix)) {
            return suffix.toLowerCase();
        }
        return switch (contentType.toLowerCase()) {
            case "image/png" -> "png";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            default -> "jpg";
        };
    }
}

package com.casy.casyaicodemother.manager.oss;

import com.aliyun.sdk.service.oss2.OSSAsyncClient;
import com.aliyun.sdk.service.oss2.models.PutObjectRequest;
import com.aliyun.sdk.service.oss2.models.PutObjectResult;
import com.aliyun.sdk.service.oss2.transport.BinaryData;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

/**
 * COS对象存储管理器
 *
 * @author yupi
 */
@Component
@Slf4j
public class OssManager {

    @Resource
    private OssClientConfig ossClientConfig;

    @Resource
    private OSSAsyncClient ossAsyncClient;

    /**
     * 上传对象
     *
     * @param key  唯一键
     * @param file 文件
     * @return 上传结果
     */
    public PutObjectResult putObject(String key, File file) {
        key = normalizeKey(key);
        try (InputStream inputStream = new FileInputStream(file)) {
            PutObjectRequest request = PutObjectRequest.newBuilder()
                    .bucket(ossClientConfig.getBucket())
                    .key(key)
                    .contentType(guessContentType(key, file))
                    .contentDisposition("inline")
                    .body(BinaryData.fromStream(inputStream, file.length()))
                    .build();
            return ossAsyncClient.putObjectAsync(request).get();
        } catch (Exception e) {
            log.error("文件上传COS失败: key={}, file={}", key, file.getAbsolutePath(), e);
            return null;
        }
    }

    /**
     * 获取文件类型，不设置默认是application/octet-stream会下载
     */
    private String guessContentType(String key, File file) {
        String name = key.contains(".") ? key : file.getName();
        int dot = name.lastIndexOf('.');
        if (dot < 0) {
            return "application/octet-stream";
        }
        return switch (name.substring(dot + 1).toLowerCase()) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "svg" -> "image/svg+xml";
            default -> "application/octet-stream";
        };
    }

    /**
     * 上传文件到 COS 并返回访问 URL
     *
     * @param key  COS对象键（完整路径）
     * @param file 要上传的文件
     * @return 文件的访问URL，失败返回null
     */
    public String uploadFile(String key, File file) {
        key = normalizeKey(key);
        PutObjectResult result = putObject(key, file);
        if (result != null) {
            String url = buildFileUrl(key);
            log.info("文件上传COS成功: {} -> {}", file.getName(), url);
            return url;
        } else {
            log.error("文件上传COS失败，返回结果为空");
            return null;
        }
    }

    private String buildFileUrl(String key) {
        if (StringUtils.hasText(ossClientConfig.getHost())) {
            String host = ossClientConfig.getHost();
            return host.endsWith("/") ? host + key : host + "/" + key;
        }
        return String.format("https://%s.oss-%s.aliyuncs.com/%s",
                ossClientConfig.getBucket(), ossClientConfig.getRegion(), key);
    }

    private static String normalizeKey(String key) {
        if (!StringUtils.hasText(key)) {
            return key;
        }
        while (key.startsWith("/")) {
            key = key.substring(1);
        }
        return key;
    }
}

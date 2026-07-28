package com.casy.casyaicodemother.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * 通用文件上传服务（聊天粘贴图片等场景）。
 */
public interface FileUploadService {

    /**
     * 校验并上传图片到对象存储。
     *
     * @param file 前端上传的图片文件
     * @return 可公网访问的 OSS URL
     */
    String uploadImage(MultipartFile file);
}

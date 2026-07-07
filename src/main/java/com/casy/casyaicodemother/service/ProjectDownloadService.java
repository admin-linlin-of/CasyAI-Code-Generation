package com.casy.casyaicodemother.service;

import jakarta.servlet.http.HttpServletResponse;

public interface ProjectDownloadService {

    /**
     * 下载代码压缩包
     *
     * @param projectPath       项目地址
     * @param downloadFileName  下载文件名
     * @param response          响应
     */
    public void downloadProjectAsZip(String projectPath, String downloadFileName, HttpServletResponse response);
}

package com.casy.casyaicodemother.service;

public interface ScreenshotService {

    /**
     * 生成并上传图片
     * @param webUrl 网站url
     * @return 图片url地址
     */
    public String generateAndUploadScreenshot(String webUrl);
}

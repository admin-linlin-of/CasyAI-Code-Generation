package com.casy.casyaicodemother.langgraph4j.tools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesis;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesisParam;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesisResult;
import com.casy.casyaicodemother.langgraph4j.state.ImageCategoryEnum;
import com.casy.casyaicodemother.langgraph4j.state.ImageResource;
import com.casy.casyaicodemother.manager.oss.OssManager;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class LogoGeneratorTool {

    @Value("${dashscope.api-key:}")
    private String dashScopeApiKey;

    @Value("${dashscope.image-model:wan2.2-t2i-flash}")
    private String imageModel;

    @Resource
    private OssManager ossManager;

    @Tool("根据描述生成 Logo 设计图片，用于网站品牌标识")
    public List<ImageResource> generateLogos(@P("Logo 设计描述，如名称、行业、风格等，尽量详细") String description) {
        List<ImageResource> logoList = new ArrayList<>();
        try {
            String logoPrompt = String.format("生成 Logo，Logo 中禁止包含任何文字！Logo 介绍：%s", description);
            ImageSynthesisParam param = ImageSynthesisParam.builder()
                    .apiKey(dashScopeApiKey)
                    .model(imageModel)
                    .prompt(logoPrompt)
                    .size("512*512")
                    .n(1)
                    .build();
            ImageSynthesis imageSynthesis = new ImageSynthesis();
            ImageSynthesisResult result = imageSynthesis.call(param);
            if (result != null && result.getOutput() != null && result.getOutput().getResults() != null) {
                List<Map<String, String>> results = result.getOutput().getResults();
                for (Map<String, String> imageResult : results) {
                    String imageUrl = imageResult.get("url");
                    if (StrUtil.isBlank(imageUrl)) {
                        continue;
                    }
                    String ossUrl = downloadAndUploadToOss(imageUrl);
                    if (StrUtil.isNotBlank(ossUrl)) {
                        logoList.add(ImageResource.builder()
                                .category(ImageCategoryEnum.LOGO)
                                .description(description)
                                .url(ossUrl)
                                .build());
                    }
                }
            }
        } catch (Exception e) {
            log.error("生成 Logo 失败: {}", e.getMessage(), e);
        }
        return logoList;
    }

    private String downloadAndUploadToOss(String imageUrl) {
        File tempFile = null;
        try {
            tempFile = FileUtil.createTempFile("logo_", ".png", true);
            HttpUtil.downloadFile(imageUrl, tempFile);
            if (!tempFile.exists() || tempFile.length() == 0) {
                log.error("Logo 下载失败或文件为空: {}", imageUrl);
                return null;
            }
            String key = String.format("logo/%s/%s.png",
                    RandomUtil.randomString(5), IdUtil.simpleUUID());
            return ossManager.uploadFile(key, tempFile);
        } catch (Exception e) {
            log.error("Logo 转存 OSS 失败: {}", imageUrl, e);
            return null;
        } finally {
            FileUtil.del(tempFile);
        }
    }
}

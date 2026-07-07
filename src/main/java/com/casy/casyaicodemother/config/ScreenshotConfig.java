package com.casy.casyaicodemother.config;

import com.casy.casyaicodemother.util.WebScreenshotUtils;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
@Slf4j
public class ScreenshotConfig {

    /**
     * 应用关闭时释放 WebDriver 与截图线程池
     */
    @PreDestroy
    public void destroyScreenshotResources() {
        WebScreenshotUtils.destroy();
    }

    /**
     * 每天凌晨2点清理过期的临时截图文件
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanupTempScreenshots() {
        log.info("开始定时清理过期的临时截图文件");
        try {
            WebScreenshotUtils.cleanupTempFiles();
            log.info("定时清理临时截图文件完成");
        } catch (Exception e) {
            log.error("定时清理临时截图文件失败", e);
        }
    }
}

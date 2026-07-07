package com.casy.casyaicodemother.util;

import cn.hutool.core.img.ImgUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.casy.casyaicodemother.exception.BusinessException;
import com.casy.casyaicodemother.exception.ErrorCode;
import io.github.bonigarcia.wdm.WebDriverManager;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
public class WebScreenshotUtils {

    private static final int DEFAULT_WIDTH = 1600;
    private static final int DEFAULT_HEIGHT = 900;

    // 懒加载，仅在单线程池工作线程中初始化，避免类加载时阻塞 HTTP 请求线程
    private static WebDriver webDriver;

    // 单线程池：所有截图任务串行执行，避免多线程并发操作同一个 WebDriver 实例
    private static final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "web-screenshot-worker");
        thread.setDaemon(true);
        return thread;
    });

    public static void cleanupTempFiles() {
        String screenshotsPath = System.getProperty("user.dir") + File.separator + "tmp" + File.separator + "screenshots";
        File localFile = new File(screenshotsPath);
        if (localFile.exists()) {
            FileUtil.del(screenshotsPath);
            log.info("本地截图文件已清理: {}", screenshotsPath);
        }
    }

    /**
     * 应用关闭时释放资源，由 ScreenshotConfig 调用
     */
    public static void destroy() {
        executor.shutdown();
        if (webDriver != null) {
            webDriver.quit();
            webDriver = null;
        }
        log.info("WebDriver 与截图线程池已关闭");
    }

    /**
     * 异步提交截图任务，实际在单线程池中串行执行
     * 调用方需 join()/get() 等待结果
     */
    public static CompletableFuture<String> takeScreenshot(String url) {
        log.info("提交截图任务，URL: {}", url);
        return CompletableFuture.supplyAsync(() -> saveWebPageScreenshot(url), executor);
    }

    /**
     * 获取 WebDriver 实例，首次调用时在单线程池工作线程中懒加载
     * 注意：此方法只能在 executor 工作线程中调用
     */
    private static WebDriver getOrInitWebDriver() {
        if (webDriver == null) {
            log.info("首次截图，开始初始化 Chrome 浏览器...");
            webDriver = initChromeDriver(DEFAULT_WIDTH, DEFAULT_HEIGHT);
            log.info("Chrome 浏览器初始化完成");
        }
        return webDriver;
    }

    /**
     * 初始化 Chrome 浏览器驱动
     */
    private static WebDriver initChromeDriver(int width, int height) {
        try {
            long start = System.currentTimeMillis();
            setupChromeDriver();
            log.info("ChromeDriver 准备完成，耗时 {}ms", System.currentTimeMillis() - start);
            ChromeOptions options = new ChromeOptions();
            // 无头模式,通过 --headless 参数，Chrome 浏览器在后台运行，不会弹出窗口。
            options.addArguments("--headless=new");
            // 禁用GPU（在某些环境下避免问题）
            options.addArguments("--disable-gpu");
            // 禁用沙盒模式（Docker环境需要）
            options.addArguments("--no-sandbox");
            // 禁用开发者shm使用
            options.addArguments("--disable-dev-shm-usage");
            // 设置窗口大小
            options.addArguments(String.format("--window-size=%d,%d", width, height));
            // 禁用扩展
            options.addArguments("--disable-extensions");
            // 跳过首次运行提示，加快启动
            options.addArguments("--no-first-run");
            // 设置用户代理
            options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
            // 创建驱动
            long driverStart = System.currentTimeMillis();
            WebDriver driver = new ChromeDriver(options);
            log.info("Chrome 浏览器启动完成，耗时 {}ms", System.currentTimeMillis() - driverStart);
            // 设置页面加载超时
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
            // 设置隐式等待
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
            return driver;
        } catch (Exception e) {
            log.error("初始化 Chrome 浏览器失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "初始化 Chrome 浏览器失败");
        }
    }

    /**
     * 配置 ChromeDriver 路径。
     * 优先使用本地已配置的驱动；未配置时通过 WebDriverManager 从 npmmirror 国内镜像下载并缓存。
     * 同时关闭 Selenium Manager，避免 new ChromeDriver 时再次访问外网。
     */
    private static void setupChromeDriver() {
        String driverPath = System.getProperty("webdriver.chrome.driver");
        if (StrUtil.isBlank(driverPath)) {
            driverPath = System.getenv("WEBDRIVER_CHROME_DRIVER");
        }
        if (StrUtil.isNotBlank(driverPath)) {
            System.setProperty("webdriver.chrome.driver", driverPath);
            // 已指定本地驱动时关闭 SeleniumManager，避免再次联网下载
            System.setProperty("selenium.manager", "false");
            return;
        }
        // npmmirror 国内镜像：<115 用 chromedriver，>=115 用 chrome-for-testing
        System.setProperty("wdm.useMirror", "true");
        System.setProperty("wdm.chromeDriverMirrorUrl", "https://registry.npmmirror.com/-/binary/chromedriver/");
        System.setProperty("wdm.chromeDriverCfTMirrorUrl", "https://registry.npmmirror.com/-/binary/chrome-for-testing/");
        WebDriverManager.chromedriver()
                .useMirror()
                .setup();
        // 禁用 Selenium Manager，避免与 WebDriverManager 重复下载
        System.setProperty("selenium.manager", "false");
    }

    /**
     * 保存图片到文件
     */
    private static void saveImage(byte[] imageBytes, String imagePath) {
        try {
            FileUtil.writeBytes(imageBytes, imagePath);
        } catch (Exception e) {
            log.error("保存图片失败: {}", imagePath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "保存图片失败");
        }
    }

    /**
     * 压缩图片
     */
    private static void compressImage(String originalImagePath, String compressedImagePath) {
        // 压缩图片质量（0.1 = 10% 质量）
        final float COMPRESSION_QUALITY = 0.3f;
        try {
            ImgUtil.compress(
                    FileUtil.file(originalImagePath),
                    FileUtil.file(compressedImagePath),
                    COMPRESSION_QUALITY
            );
        } catch (Exception e) {
            log.error("压缩图片失败: {} -> {}", originalImagePath, compressedImagePath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "压缩图片失败");
        }
    }

    /**
     * 等待页面加载完成
     */
    private static void waitForPageLoad(WebDriver driver) {
        try {
            // 创建等待页面加载对象
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            // 等待 document.readyState 为complete
            wait.until(webDriver ->
                    ((JavascriptExecutor) webDriver).executeScript("return document.readyState")
                            .equals("complete")
            );
            // 额外等待一段时间，确保动态内容加载完成
            Thread.sleep(2000);
            log.info("页面加载完成");
        } catch (Exception e) {
            log.error("等待页面加载时出现异常，继续执行截图", e);
        }
    }

    /**
     * 生成网页截图
     *
     * @param webUrl 网页URL
     * @return 压缩后的截图文件路径，失败返回null
     */
    private static String saveWebPageScreenshot(String webUrl) {
        if (StrUtil.isBlank(webUrl)) {
            log.error("网页URL不能为空");
            return null;
        }
        try {
            WebDriver driver = getOrInitWebDriver();
            // 创建临时目录
            String rootPath = System.getProperty("user.dir") + File.separator + "tmp" + File.separator + "screenshots"
                    + File.separator + UUID.randomUUID().toString().substring(0, 8);
            FileUtil.mkdir(rootPath);
            // 图片后缀
            final String IMAGE_SUFFIX = ".png";
            // 原始截图文件路径
            String imageSavePath = rootPath + File.separator + RandomUtil.randomNumbers(5) + IMAGE_SUFFIX;
            // 访问网页
            driver.get(webUrl);
            // 等待页面加载完成
            waitForPageLoad(driver);
            // 截图
            byte[] screenshotBytes = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
            // 保存原始图片
            saveImage(screenshotBytes, imageSavePath);
            log.info("原始截图保存成功：{}", imageSavePath);
            // 压缩图片
            final String COMPRESSION_SUFFIX = "_compressed.jpg";
            String compressedImagePath = rootPath + File.separator + RandomUtil.randomNumbers(5) + COMPRESSION_SUFFIX;
            // 参数顺序：源文件(原始png) -> 目标文件(压缩jpg)
            compressImage(imageSavePath, compressedImagePath);
            log.info("压缩图片保存成功：{}", compressedImagePath);
            // 删除原始图片，只保留压缩图片
            FileUtil.del(imageSavePath);
            return compressedImagePath;
        } catch(Exception e) {
            log.error("网页截图失败：{}", webUrl, e);
            return null;
        }
    }

}

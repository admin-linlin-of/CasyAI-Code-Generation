package com.casy.casyaicodemother.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class WebScreenshotUtilsTest {


    @Test
    void saveWebPageScreenshot() {
        String testUrl = "https://www.codefather.cn";
        String webPageScreenshot = WebScreenshotUtils.takeScreenshot(testUrl).join();
        Assertions.assertNotNull(webPageScreenshot);
    }
}
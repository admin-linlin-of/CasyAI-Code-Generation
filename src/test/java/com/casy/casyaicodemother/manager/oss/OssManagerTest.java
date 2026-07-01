package com.casy.casyaicodemother.manager.oss;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.File;

@SpringBootTest(classes = {OssClientConfig.class, OssManager.class})
@EnableConfigurationProperties(OssClientConfig.class)
@ActiveProfiles("local")
class OssManagerTest {

    @Resource
    private OssManager ossManager;

    @Test
    void uploadFile() {
        File file = new File("D:\\myProject\\casy-ai-code-mother\\casy-ai-code-mother\\tmp\\screenshots\\990470a7\\55044_compressed.jpg");
        String url = ossManager.uploadFile("1111.jpg", file);
        assert url != null;
    }
}
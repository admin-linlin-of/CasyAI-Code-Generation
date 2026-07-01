package com.casy.casyaicodemother.manager.oss;

import com.aliyun.sdk.service.oss2.OSSAsyncClient;
import com.aliyun.sdk.service.oss2.credentials.CredentialsProvider;
import com.aliyun.sdk.service.oss2.credentials.StaticCredentialsProvider;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * 阿云COS配置类
 * 
 * @author yupi
 */
@Configuration
@ConfigurationProperties(prefix = "cos.client")
@Data
public class OssClientConfig {

    /**
     * 域名
     */
    private String host;

    /**
     * secretId
     */
    private String secretId;

    /**
     * 密钥（注意不要泄露）
     */
    private String secretKey;

    /**
     * 区域
     */
    private String region;

    /**
     * 桶名
     */
    private String bucket;

    @Bean(destroyMethod = "close") //Spring 在销毁这个 Bean 时会自动调用 close()，防止资源泄露
    public OSSAsyncClient cosClient() {
        CredentialsProvider credentialsProvider = new StaticCredentialsProvider(secretId, secretKey);
        var builder = OSSAsyncClient.newBuilder()
                .credentialsProvider(credentialsProvider)
                .region(region);
        if (StringUtils.hasText(host)) {
            builder.endpoint(host).useCName(true); // 自定义域名，由于要预览图片，必须使用自定义的域名
        }
        return builder.build();
    }
}

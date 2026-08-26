package com.casy.casyaicodemother.service;

import com.casy.casyaicodemother.model.dto.app.AppVersionRequest;
import com.casy.casyaicodemother.model.entity.AppVersion;
import com.casy.casyaicodemother.model.entity.User;
import com.casy.casyaicodemother.model.enums.ModelTypeEnum;
import com.casy.casyaicodemother.model.enums.VersionBuildStatusEnum;
import com.casy.casyaicodemother.model.enums.VersionDeployStatusEnum;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 应用代码版本 服务层。
 *
 * @author <a href="https://gitee.com/linlinyes/casy-ai-code-mother">程序员Casy</a>
 */
public interface AppVersionService extends IService<AppVersion> {


    String createCodeVersion(Long appId, ModelTypeEnum modelTypeEnum, Long userMessageId);

    QueryWrapper getQueryWrapper(AppVersionRequest appVersionRequest);

    void removeByAppVersion(AppVersion appVersion);

    List<AppVersion> getAppVersionsByAppId(Long appid, User loginUser);

    Page<AppVersion> listAppVersionByPage(AppVersionRequest appVersionRequest);

    AppVersion getByAppIdAndCodeDir(Long appId, String codeDir);

    void updateBuildStatus(Long appId, String codeDir, VersionBuildStatusEnum buildStatus);

    /**
     * 更新构建状态；失败时可写入 buildError 供前端展示 npm 输出摘要。
     */
    void updateBuildStatus(Long appId, String codeDir, VersionBuildStatusEnum buildStatus, String buildError);

    void updateDeployStatus(Long appId, String codeDir, VersionDeployStatusEnum deployStatus);

    String getLatestCodeDir(Long appId);

    void retryBuild(Long appId, String codeDir, User loginUser);

    void buildVersion(Long appId, String codeDir, User loginUser);

    /**
     * 建立 SSE 连接：触发（或订阅）Vue 版本打包，并实时推送 build_status 事件。
     * <p>
     * 替代原 {@code POST /build} + 前端轮询方案；打包仍在虚拟线程中异步执行，不阻塞 Servlet 线程。
     *
     * @param appId          应用 ID
     * @param codeDir        版本目录
     * @param loginUser      当前登录用户（鉴权）
     * @param skipIfSuccess  true 时若 DB 已是 success/failed 则直接推送当前状态、不再触发 build
     *                       （工作流模式 ProjectBuilderNode 已同步打包时使用）
     * @return SseEmitter，由 Spring MVC 持有至连接关闭
     */
    SseEmitter buildVersionStream(Long appId, String codeDir, User loginUser, boolean skipIfSuccess);
}

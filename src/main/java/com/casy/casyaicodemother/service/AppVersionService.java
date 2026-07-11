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
}

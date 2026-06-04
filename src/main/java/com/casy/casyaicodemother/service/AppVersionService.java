package com.casy.casyaicodemother.service;

import com.casy.casyaicodemother.model.dto.app.AppVersionRequest;
import com.casy.casyaicodemother.model.entity.AppVersion;
import com.casy.casyaicodemother.model.entity.User;
import com.casy.casyaicodemother.model.enums.ModelTypeEnum;
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
}

package com.casy.casyaicodemother.service;

import com.casy.casyaicodemother.model.dto.sysparam.SysParamAddRequest;
import com.casy.casyaicodemother.model.dto.sysparam.SysParamUpdateRequest;
import com.casy.casyaicodemother.model.entity.SysParam;

import java.util.List;
import java.util.Map;

public interface SysParamService {

    List<SysParam> listAll();

    /** 已启用的公开参数：键 → 值，供首页等未登录页面使用 */
    Map<String, String> listPublicValues();

    long addParam(SysParamAddRequest request);

    boolean updateParam(SysParamUpdateRequest request);

    boolean deleteParam(Long id);
}

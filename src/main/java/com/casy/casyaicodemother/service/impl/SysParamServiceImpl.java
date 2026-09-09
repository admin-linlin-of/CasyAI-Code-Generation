package com.casy.casyaicodemother.service.impl;

import cn.hutool.core.util.StrUtil;
import com.casy.casyaicodemother.exception.ErrorCode;
import com.casy.casyaicodemother.exception.ThrowUtils;
import com.casy.casyaicodemother.mapper.SysParamMapper;
import com.casy.casyaicodemother.model.dto.sysparam.SysParamAddRequest;
import com.casy.casyaicodemother.model.dto.sysparam.SysParamUpdateRequest;
import com.casy.casyaicodemother.model.entity.SysParam;
import com.casy.casyaicodemother.service.SysParamService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SysParamServiceImpl extends ServiceImpl<SysParamMapper, SysParam> implements SysParamService {

    @Override
    public List<SysParam> listAll() {
        return list(QueryWrapper.create().orderBy(SysParam::getSortOrder, true));
    }

    @Override
    public Map<String, String> listPublicValues() {
        List<SysParam> params = list(QueryWrapper.create()
                .eq(SysParam::getEnabled, 1)
                .eq(SysParam::getIsPublic, 1)
                .orderBy(SysParam::getSortOrder, true));
        Map<String, String> result = new LinkedHashMap<>();
        for (SysParam param : params) {
            if (StrUtil.isBlank(param.getParamKey()) || StrUtil.isBlank(param.getParamValue())) {
                continue;
            }
            result.put(param.getParamKey(), param.getParamValue().trim());
        }
        return result;
    }

    @Override
    public long addParam(SysParamAddRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        String paramKey = StrUtil.trim(request.getParamKey());
        String paramName = StrUtil.trim(request.getParamName());
        ThrowUtils.throwIf(StrUtil.isBlank(paramKey), ErrorCode.PARAMS_ERROR, "参数键不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(paramName), ErrorCode.PARAMS_ERROR, "参数名称不能为空");
        ThrowUtils.throwIf(existsByKey(paramKey, null), ErrorCode.PARAMS_ERROR, "参数键已存在");
        String paramValue = StrUtil.trim(request.getParamValue());
        validateUrlValue(paramKey, paramValue);

        SysParam param = SysParam.builder()
                .paramKey(paramKey)
                .paramValue(paramValue)
                .paramName(paramName)
                .remark(StrUtil.trim(request.getRemark()))
                .enabled(request.getEnabled() == null ? 1 : request.getEnabled())
                .isPublic(request.getIsPublic() == null ? 0 : request.getIsPublic())
                .sortOrder(request.getSortOrder() == null ? 100 : request.getSortOrder())
                .build();
        ThrowUtils.throwIf(param.getEnabled() != 0 && param.getEnabled() != 1, ErrorCode.PARAMS_ERROR, "enabled 只能为 0 或 1");
        ThrowUtils.throwIf(param.getIsPublic() != 0 && param.getIsPublic() != 1, ErrorCode.PARAMS_ERROR, "isPublic 只能为 0 或 1");
        boolean saved = save(param);
        ThrowUtils.throwIf(!saved, ErrorCode.OPERATION_ERROR, "新增参数失败");
        return param.getId();
    }

    @Override
    public boolean updateParam(SysParamUpdateRequest request) {
        ThrowUtils.throwIf(request == null || request.getId() == null, ErrorCode.PARAMS_ERROR);
        SysParam existing = getById(request.getId());
        ThrowUtils.throwIf(existing == null, ErrorCode.NOT_FOUND_ERROR, "参数不存在");

        String paramKey = StrUtil.trim(request.getParamKey());
        String paramName = StrUtil.trim(request.getParamName());
        ThrowUtils.throwIf(StrUtil.isBlank(paramKey), ErrorCode.PARAMS_ERROR, "参数键不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(paramName), ErrorCode.PARAMS_ERROR, "参数名称不能为空");
        ThrowUtils.throwIf(existsByKey(paramKey, existing.getId()), ErrorCode.PARAMS_ERROR, "参数键已存在");
        String paramValue = StrUtil.trim(request.getParamValue());
        validateUrlValue(paramKey, paramValue);

        Integer enabled = request.getEnabled() == null ? existing.getEnabled() : request.getEnabled();
        Integer isPublic = request.getIsPublic() == null ? existing.getIsPublic() : request.getIsPublic();
        ThrowUtils.throwIf(enabled != 0 && enabled != 1, ErrorCode.PARAMS_ERROR, "enabled 只能为 0 或 1");
        ThrowUtils.throwIf(isPublic != 0 && isPublic != 1, ErrorCode.PARAMS_ERROR, "isPublic 只能为 0 或 1");

        existing.setParamKey(paramKey);
        existing.setParamValue(paramValue);
        existing.setParamName(paramName);
        existing.setRemark(StrUtil.trim(request.getRemark()));
        existing.setEnabled(enabled);
        existing.setIsPublic(isPublic);
        existing.setSortOrder(request.getSortOrder() == null ? existing.getSortOrder() : request.getSortOrder());
        return updateById(existing);
    }

    @Override
    public boolean deleteParam(Long id) {
        ThrowUtils.throwIf(id == null, ErrorCode.PARAMS_ERROR);
        SysParam existing = getById(id);
        ThrowUtils.throwIf(existing == null, ErrorCode.NOT_FOUND_ERROR, "参数不存在");
        return removeById(id);
    }

    private boolean existsByKey(String paramKey, Long excludeId) {
        QueryWrapper query = QueryWrapper.create().eq(SysParam::getParamKey, paramKey);
        if (excludeId != null) {
            query.ne(SysParam::getId, excludeId);
        }
        return count(query) > 0;
    }

    /** 站点链接类参数必须是 http(s)，避免 javascript: 等危险协议 */
    private static void validateUrlValue(String paramKey, String paramValue) {
        if (StrUtil.isBlank(paramValue)) {
            return;
        }
        if (paramKey != null && paramKey.endsWith(".url")) {
            String lower = paramValue.toLowerCase();
            ThrowUtils.throwIf(!lower.startsWith("http://") && !lower.startsWith("https://"),
                    ErrorCode.PARAMS_ERROR, "链接参数必须以 http:// 或 https:// 开头");
        }
    }
}

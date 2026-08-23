package com.casy.casyaicodemother.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.StrUtil;
import com.casy.casyaicodemother.exception.ErrorCode;
import com.casy.casyaicodemother.exception.ThrowUtils;
import com.casy.casyaicodemother.mapper.AiModelMapper;
import com.casy.casyaicodemother.model.dto.aimodel.AiModelAddRequest;
import com.casy.casyaicodemother.model.dto.aimodel.AiModelUpdateRequest;
import com.casy.casyaicodemother.model.entity.AiModel;
import com.casy.casyaicodemother.model.enums.ModelTypeEnum;
import com.casy.casyaicodemother.service.AiModelCatalogService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AiModelCatalogServiceImpl extends ServiceImpl<AiModelMapper, AiModel> implements AiModelCatalogService {

    /** 路由 prompt 骨架，可用模型列表由数据库动态填入 */
    private static final String PROMPT_TEMPLATE = ResourceUtil.readUtf8Str("prompt/codegen-routing-model-prompt.txt");

    @Override
    public List<AiModel> listEnabled() {
        List<AiModel> models = list(QueryWrapper.create()
                .eq(AiModel::getEnabled, 1)
                .orderBy(AiModel::getSortOrder, true));
        ThrowUtils.throwIf(CollUtil.isEmpty(models), ErrorCode.SYSTEM_ERROR, "没有可用的 AI 模型，请在 t_ai_model 中至少启用一个");
        return models;
    }

    @Override
    public List<AiModel> listAll() {
        return list(QueryWrapper.create().orderBy(AiModel::getSortOrder, true));
    }

    @Override
    public ModelTypeEnum getDefaultEnum() {
        List<AiModel> enabled = listEnabled();
        AiModel defaultModel = enabled.stream()
                .filter(m -> Integer.valueOf(1).equals(m.getIsDefault()))
                .findFirst()
                .orElse(enabled.getFirst());
        ModelTypeEnum type = ModelTypeEnum.fromCodeOrModelName(defaultModel.getModelCode());
        ThrowUtils.throwIf(type == null, ErrorCode.SYSTEM_ERROR, "默认模型配置无效: " + defaultModel.getModelCode());
        return type;
    }

    @Override
    public ModelTypeEnum requireEnabled(String modelCodeOrName) {
        ModelTypeEnum type = ModelTypeEnum.fromCodeOrModelName(modelCodeOrName);
        ThrowUtils.throwIf(type == null, ErrorCode.PARAMS_ERROR, "不支持的AI模型");
        boolean enabled = listEnabled().stream().anyMatch(m -> type.name().equals(m.getModelCode()));
        ThrowUtils.throwIf(!enabled, ErrorCode.PARAMS_ERROR, "模型已停用: " + type.getModelName());
        return type;
    }

    @Override
    public ModelTypeEnum resolveEnabledOrDefault(ModelTypeEnum routed) {
        if (routed == null) {
            return getDefaultEnum();
        }
        boolean enabled = listEnabled().stream().anyMatch(m -> routed.name().equals(m.getModelCode()));
        return enabled ? routed : getDefaultEnum();
    }

    @Override
    public String buildRoutingPrompt() {
        List<AiModel> enabled = listEnabled();
        StringBuilder models = new StringBuilder();
        StringBuilder rules = new StringBuilder();
        int i = 1;
        for (AiModel model : enabled) {
            models.append(i).append(". ").append(model.getModelCode()).append(" - ").append(StrUtil.blankToDefault(model.getDescription(), "")).append("\n");
            if (StrUtil.isNotBlank(model.getRoutingRule())) {
                rules.append("- ").append(model.getRoutingRule()).append("\n");
            }
            i++;
        }
        String enumList = enabled.stream().map(AiModel::getModelCode).collect(Collectors.joining("、"));
        String priority = enabled.stream()
                .sorted(Comparator.comparingInt(m -> m.getSortOrder() == null ? 100 : m.getSortOrder()))
                .map(AiModel::getModelCode)
                .collect(Collectors.joining(" > "));
        String defaultCode = getDefaultEnum().name();
        return PROMPT_TEMPLATE
                .replace("{{AVAILABLE_MODELS}}", models.toString().trim())
                .replace("{{ROUTING_RULES}}", rules.toString().trim())
                .replace("{{PRIORITY}}", priority)
                .replace("{{DEFAULT_MODEL}}", defaultCode)
                .replace("{{ENUM_LIST}}", enumList);
    }

    @Override
    public boolean updateEnabled(Long id, Integer enabled) {
        ThrowUtils.throwIf(id == null || enabled == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(enabled != 0 && enabled != 1, ErrorCode.PARAMS_ERROR, "enabled 只能为 0 或 1");
        AiModel model = getById(id);
        ThrowUtils.throwIf(model == null, ErrorCode.NOT_FOUND_ERROR, "模型不存在");
        if (enabled == 0) {
            long remain = listEnabled().stream().filter(m -> !m.getId().equals(id)).count();
            ThrowUtils.throwIf(remain <= 0, ErrorCode.PARAMS_ERROR, "至少保留一个可用模型");
        }
        model.setEnabled(enabled);
        return updateById(model);
    }

    @Override
    public long addModel(AiModelAddRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        String modelCode = StrUtil.trim(request.getModelCode());
        String modelName = StrUtil.trim(request.getModelName());
        ThrowUtils.throwIf(StrUtil.isBlank(modelCode), ErrorCode.PARAMS_ERROR, "模型编码不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(modelName), ErrorCode.PARAMS_ERROR, "模型名称不能为空");
        ThrowUtils.throwIf(ModelTypeEnum.fromCodeOrModelName(modelCode) == null,
                ErrorCode.PARAMS_ERROR, "模型编码必须与 ModelTypeEnum 一致");
        long exists = count(QueryWrapper.create().eq(AiModel::getModelCode, modelCode));
        ThrowUtils.throwIf(exists > 0, ErrorCode.PARAMS_ERROR, "模型编码已存在");
        int enabled = request.getEnabled() == null ? 1 : request.getEnabled();
        int isDefault = request.getIsDefault() == null ? 0 : request.getIsDefault();
        ThrowUtils.throwIf(enabled != 0 && enabled != 1, ErrorCode.PARAMS_ERROR, "enabled 只能为 0 或 1");
        ThrowUtils.throwIf(isDefault != 0 && isDefault != 1, ErrorCode.PARAMS_ERROR, "isDefault 只能为 0 或 1");
        ThrowUtils.throwIf(isDefault == 1 && enabled == 0, ErrorCode.PARAMS_ERROR, "停用模型不能设为默认");
        AiModel model = AiModel.builder()
                .modelCode(modelCode)
                .modelName(modelName)
                .enabled(enabled)
                .isDefault(isDefault)
                .sortOrder(request.getSortOrder() == null ? 100 : request.getSortOrder())
                .description(request.getDescription())
                .routingRule(request.getRoutingRule())
                .build();
        boolean saved = save(model);
        ThrowUtils.throwIf(!saved, ErrorCode.OPERATION_ERROR, "新增模型失败");
        if (isDefault == 1) {
            clearOtherDefaults(model.getId());
        }
        return model.getId();
    }

    @Override
    public boolean updateModel(AiModelUpdateRequest request) {
        ThrowUtils.throwIf(request == null || request.getId() == null, ErrorCode.PARAMS_ERROR);
        AiModel old = getById(request.getId());
        ThrowUtils.throwIf(old == null, ErrorCode.NOT_FOUND_ERROR, "模型不存在");
        if (StrUtil.isNotBlank(request.getModelCode()) && !request.getModelCode().equals(old.getModelCode())) {
            ThrowUtils.throwIf(ModelTypeEnum.fromCodeOrModelName(request.getModelCode()) == null,
                    ErrorCode.PARAMS_ERROR, "模型编码必须与 ModelTypeEnum 一致");
            long exists = count(QueryWrapper.create()
                    .eq(AiModel::getModelCode, request.getModelCode())
                    .ne(AiModel::getId, old.getId()));
            ThrowUtils.throwIf(exists > 0, ErrorCode.PARAMS_ERROR, "模型编码已存在");
            old.setModelCode(StrUtil.trim(request.getModelCode()));
        }
        if (StrUtil.isNotBlank(request.getModelName())) {
            old.setModelName(StrUtil.trim(request.getModelName()));
        }
        if (request.getSortOrder() != null) {
            old.setSortOrder(request.getSortOrder());
        }
        if (request.getDescription() != null) {
            old.setDescription(request.getDescription());
        }
        if (request.getRoutingRule() != null) {
            old.setRoutingRule(request.getRoutingRule());
        }
        Integer enabled = request.getEnabled();
        if (enabled != null) {
            ThrowUtils.throwIf(enabled != 0 && enabled != 1, ErrorCode.PARAMS_ERROR, "enabled 只能为 0 或 1");
            if (enabled == 0) {
                ensureNotLastEnabled(old.getId());
            }
            old.setEnabled(enabled);
        }
        Integer isDefault = request.getIsDefault();
        if (isDefault != null) {
            ThrowUtils.throwIf(isDefault != 0 && isDefault != 1, ErrorCode.PARAMS_ERROR, "isDefault 只能为 0 或 1");
            ThrowUtils.throwIf(isDefault == 1 && Integer.valueOf(0).equals(old.getEnabled()),
                    ErrorCode.PARAMS_ERROR, "停用模型不能设为默认");
            old.setIsDefault(isDefault);
        }
        boolean updated = updateById(old);
        if (updated && Integer.valueOf(1).equals(old.getIsDefault())) {
            clearOtherDefaults(old.getId());
        }
        return updated;
    }

    @Override
    public boolean deleteModel(Long id) {
        ThrowUtils.throwIf(id == null, ErrorCode.PARAMS_ERROR);
        AiModel model = getById(id);
        ThrowUtils.throwIf(model == null, ErrorCode.NOT_FOUND_ERROR, "模型不存在");
        if (Integer.valueOf(1).equals(model.getEnabled())) {
            ensureNotLastEnabled(id);
        }
        return removeById(id);
    }

    private void ensureNotLastEnabled(Long id) {
        long remain = listEnabled().stream().filter(m -> !m.getId().equals(id)).count();
        ThrowUtils.throwIf(remain <= 0, ErrorCode.PARAMS_ERROR, "至少保留一个可用模型");
    }

    private void clearOtherDefaults(Long keepId) {
        List<AiModel> all = listAll();
        for (AiModel model : all) {
            if (!model.getId().equals(keepId) && Integer.valueOf(1).equals(model.getIsDefault())) {
                model.setIsDefault(0);
                updateById(model);
            }
        }
    }
}

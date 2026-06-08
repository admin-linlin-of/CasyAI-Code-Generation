package com.casy.casyaicodemother.model.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

@Getter
public enum ModelTypeEnum {

    GPT("gpt-5.5"),
    DEEPSEEKFLASH("deepseek-v4-flash"),
    DEEPSEEKPRO("deepseek-v4-pro"),
    CLAUDESONNET("claude-sonnet-4-6");

    private final String modelName;

    ModelTypeEnum(String modelName) {
        this.modelName = modelName;
    }

    /**
     * 根据 modelName 获取枚举
     *
     * @param modelName 枚举值的modelName
     * @return 枚举值
     */
    public static ModelTypeEnum getEnumByModelName(String modelName) {
        if (ObjUtil.isEmpty(modelName)) {
            return null;
        }
        for (ModelTypeEnum anEnum : ModelTypeEnum.values()) {
            if (anEnum.modelName.equals(modelName)) {
                return anEnum;
            }
        }
        return null;
    }
}

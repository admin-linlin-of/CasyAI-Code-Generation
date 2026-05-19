package com.casy.casyaicodemother.model.enums;

import lombok.Getter;

@Getter
public enum ModelTypeEnum {

    GPT("gpt-5.5"),
    DEEPSEEK("deepseek-v4-flash");

    private final String modelName;

    ModelTypeEnum(String modelName) {
        this.modelName = modelName;
    }
}

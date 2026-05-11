package com.casy.casyaicodemother.core;

import com.casy.casyaicodemother.ai.AiCodeGeneratorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * AI 代码生成外观类，组合生成和保存功能
 */
@Service
public class AiCodeGeneratorFacade {

    @Autowired
    private AiCodeGeneratorService aiCodeGeneratorService;


}

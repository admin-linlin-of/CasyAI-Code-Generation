package com.casy.casyaicodemother.config;

import com.casy.casyaicodemother.langgraph4j.workflow.CodeGenWorkflow;
import org.bsc.langgraph4j.studio.LangGraphStudioServer;
import org.bsc.langgraph4j.studio.springboot.LangGraphStudioConfig;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * langgraph4j-studio的Bean
 */
@Configuration
public class LangGraphStudioSampleConfig extends LangGraphStudioConfig {

    @Override
    public Map<String, LangGraphStudioServer.Instance> instanceMap() {
        var workflow = new CodeGenWorkflow().createWorkflow().stateGraph;

        var instance = LangGraphStudioServer.Instance.builder()
                .title("代码生成流程图")
                .graph(workflow)
                .build();

        return Map.of("default", instance);
    }
}
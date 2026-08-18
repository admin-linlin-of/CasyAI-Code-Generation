package com.casy.casyaicodemother.langgraph4j.node;

import com.casy.casyaicodemother.langgraph4j.ai.ImageCollectionService;
import com.casy.casyaicodemother.langgraph4j.state.ImageCollectionResult;
import com.casy.casyaicodemother.langgraph4j.state.WorkflowContext;
import com.casy.casyaicodemother.util.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.util.Collections;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 图片收集节点
 * 使用AI进行工具调用，收集不同类型的图片
 */
@Slf4j
public class ImageCollectorNode {

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            String originalPrompt = context.getOriginalPrompt();
            ImageCollectionResult imageCollectionResult = new ImageCollectionResult();
            try {
                // 获取AI图片收集服务
                ImageCollectionService imageCollectionService = SpringContextUtil.getBean(ImageCollectionService.class);
                // 使用 AI 服务进行智能图片收集
                imageCollectionResult = imageCollectionService.collectImages(originalPrompt);
            } catch (Exception e) {
                log.error("图片收集失败: {}", e.getMessage(), e);
            }
            // 更新状态
            context.setCurrentStep("图片收集");
            context.setImageList(imageCollectionResult.getValues() != null
                    ? imageCollectionResult.getValues()
                    : Collections.emptyList());
            return WorkflowContext.saveContext(context);
        });
    }
}

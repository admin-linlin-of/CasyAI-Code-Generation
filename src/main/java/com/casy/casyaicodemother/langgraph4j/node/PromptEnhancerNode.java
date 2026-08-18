package com.casy.casyaicodemother.langgraph4j.node;

import cn.hutool.core.collection.CollUtil;
import com.casy.casyaicodemother.langgraph4j.state.ImageResource;
import com.casy.casyaicodemother.langgraph4j.state.WorkflowContext;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.util.List;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 增强提示词
 * 示例：
 * ## 可用素材资源
 * 请在生成网站使用以下图片资源，将这些图片合理地嵌入到网站的相应位置中。
 * - 架构图：编程学习网站架构（https://www.codefather.cn/logo.png）
 * - Logo图片：编程导航学习圈的 Logo（https://www.codefather.cn/logo.png）
 */
@Slf4j
public class PromptEnhancerNode {

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            log.info("执行节点: 提示词增强");
            // 获取原始提示词和图片列表
            String originalPrompt = context.getOriginalPrompt();

            List<ImageResource> imageList = context.getImageList();
            // 构建增强后的提示词
            StringBuilder enhancedPromptBuilder = new StringBuilder();
            enhancedPromptBuilder.append(originalPrompt);
            // 如果有图片资源，则添加图片信息
            if (CollUtil.isNotEmpty(imageList)) {
                enhancedPromptBuilder.append("\n\n## 可用素材资源\n");
                enhancedPromptBuilder.append("请在生成网站使用以下图片资源，将这些图片合理地嵌入到网站的相应位置中。\n");
                for (ImageResource image : imageList) {
                    enhancedPromptBuilder.append("- ")
                            .append(image.getCategory().getText())
                            .append("：")
                            .append(image.getDescription())
                            .append("（")
                            .append(image.getUrl())
                            .append("）\n");
                }
            }
            String enhancedPrompt = enhancedPromptBuilder.toString();
            // 更新状态
            context.setCurrentStep("提示词增强");
            context.setEnhancedPrompt(enhancedPrompt);
            log.info("提示词增强完成，增强后长度: {} 字符", enhancedPrompt.length());
            return WorkflowContext.saveContext(context);
        });
    }
}


package com.casy.casyaicodemother.langgraph4j.node;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.casy.casyaicodemother.langgraph4j.state.ImageResource;
import com.casy.casyaicodemother.langgraph4j.state.WorkflowContext;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.util.List;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 增强提示词。
 * <p>
 * 首次生成：原始需求 + 本轮搜到的图片。
 * 修改模式：先声明「改已有站点」，再附文件清单，要求局部修改而不是整站重写。
 */
@Slf4j
public class PromptEnhancerNode {

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            log.info("执行节点: 提示词增强");
            String originalPrompt = context.getOriginalPrompt();
            List<ImageResource> imageList = context.getImageList();
            StringBuilder enhancedPromptBuilder = new StringBuilder();

            // 修改模式：把「增量改」写进提示词，模型才会用读写工具而不是当新项目生成
            if (Boolean.TRUE.equals(context.getEditMode())) {
                enhancedPromptBuilder.append("【这是对已有网站的修改，不是从零生成新站点】\n");
                enhancedPromptBuilder.append("用户本轮修改要求：\n");
                enhancedPromptBuilder.append(originalPrompt).append('\n');
                if (StrUtil.isNotBlank(context.getExistingCodeDir())) {
                    enhancedPromptBuilder.append("\n已有代码目录：").append(context.getExistingCodeDir()).append('\n');
                }
                if (StrUtil.isNotBlank(context.getExistingFileSummary())) {
                    enhancedPromptBuilder.append("\n已有文件（请先读取相关文件再局部修改，禁止无故重写整个项目）：\n");
                    enhancedPromptBuilder.append(context.getExistingFileSummary()).append('\n');
                }
                enhancedPromptBuilder.append("""
                        
                        修改要求：
                        1. 只改与本轮需求相关的文件，保持现有结构、命名和视觉风格。
                        2. Vue 工程用文件工具读写；HTML/多文件在原页面上改，不要换成另一个网站。
                        3. 不要删除用户没提到的功能、路由和素材。
                        """);
            } else {
                enhancedPromptBuilder.append(originalPrompt);
            }

            if (CollUtil.isNotEmpty(imageList)) {
                enhancedPromptBuilder.append("\n\n## 可用素材资源\n");
                enhancedPromptBuilder.append("请在网站中合理使用以下图片资源。\n");
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
            context.setCurrentStep("提示词增强");
            context.setEnhancedPrompt(enhancedPrompt);
            log.info("提示词增强完成，editMode={}, 长度={}", context.getEditMode(), enhancedPrompt.length());
            return WorkflowContext.saveContext(context);
        });
    }
}


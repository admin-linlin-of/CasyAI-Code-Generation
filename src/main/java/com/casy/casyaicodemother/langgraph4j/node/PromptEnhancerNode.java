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

    /** 增强后提示词总字数上限（素材区在剩余预算内整条附加，需求区/修改说明永不截断） */
    private static final int MAX_ENHANCED_CHARS = 3000;
    /** 单条素材描述超过该长度时直接丢弃描述，只保留「用途分类 + URL」 */
    private static final int MAX_IMAGE_DESC_CHARS = 60;
    /** 修改模式：已有文件清单截断长度（按区块裁剪，另行处理） */
    private static final int MAX_FILE_SUMMARY_CHARS = 1200;

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
                    // 文件清单可能很长，截断防止增强提示词膨胀到上万字
                    enhancedPromptBuilder
                            .append(StrUtil.maxLength(context.getExistingFileSummary(), MAX_FILE_SUMMARY_CHARS))
                            .append('\n');
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

            // ── 素材区（区块化裁剪）：──────────────────────────────────────────────
            // 1) 需求区/修改说明已经拼在上面的 enhancedPromptBuilder 里，永不截断；
            // 2) 素材压缩：长描述(>60字)直接丢弃，只保留「用途分类 + URL」；
            // 3) 素材在「总预算剩余空间」内按整条 append；放不下就整条丢弃该条（以及后续素材），
            //    绝不把某一行/需求文字切断成半行，丢弃数量写入日志便于观测。
            String basePrompt = enhancedPromptBuilder.toString();
            int used = basePrompt.length();
            int appendedImages = 0;
            int droppedByBudget = 0;
            int invalidImages = 0;
            if (CollUtil.isNotEmpty(imageList)) {
                // 素材小节标题只在成功追加第一条时写入
                final String sectionHead = "\n\n## 可用素材资源\n请在网站中合理使用以下图片资源。";
                for (ImageResource image : imageList) {
                    if (image == null || StrUtil.isBlank(image.getUrl())) {
                        invalidImages++;
                        continue;
                    }
                    String category = image.getCategory() == null ? "素材"
                            : StrUtil.blankToDefault(image.getCategory().getText(), "素材");
                    String desc = StrUtil.blankToDefault(image.getDescription(), "").trim();
                    // 描述只保留 ≤60 字的一句话；超长直接丢弃描述（URL 才是生成代码会用到的）
                    String descSuffix = (!desc.isEmpty() && desc.length() <= MAX_IMAGE_DESC_CHARS)
                            ? "（" + desc + "）"
                            : "";
                    String line = "- " + category + "：" + image.getUrl() + descSuffix;
                    int headLen = appendedImages == 0 ? sectionHead.length() : 0;
                    int addLen = headLen + 1 + line.length(); // 1 = 行首换行
                    if (used + addLen > MAX_ENHANCED_CHARS) {
                        droppedByBudget++;
                        break; // 越靠后只会越满，后续素材整条放弃
                    }
                    if (appendedImages == 0) {
                        enhancedPromptBuilder.append(sectionHead);
                    }
                    enhancedPromptBuilder.append('\n').append(line);
                    used += addLen;
                    appendedImages++;
                }
            }
            if (droppedByBudget > 0) {
                log.warn("素材预算不足：需求区占用较大，素材已附 {} 条、因预算整条丢弃 {} 条（需求/修改说明保持完整）",
                        appendedImages, droppedByBudget);
            }
            if (invalidImages > 0) {
                log.debug("跳过 {} 条无效素材（无 URL）", invalidImages);
            }
            String enhancedPrompt = enhancedPromptBuilder.toString();
            context.setCurrentStep("提示词增强");
            context.setEnhancedPrompt(enhancedPrompt);
            log.info("提示词增强完成，editMode={}, 原始={} 字, 增强后={} 字",
                    context.getEditMode(), originalPrompt == null ? 0 : originalPrompt.length(), enhancedPrompt.length());
            return WorkflowContext.saveContext(context);
        });
    }
}


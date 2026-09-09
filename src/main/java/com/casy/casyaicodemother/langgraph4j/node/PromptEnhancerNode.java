package com.casy.casyaicodemother.langgraph4j.node;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.casy.casyaicodemother.langgraph4j.state.ImageCategoryEnum;
import com.casy.casyaicodemother.langgraph4j.state.ImageResource;
import com.casy.casyaicodemother.langgraph4j.state.WorkflowContext;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 增强提示词。
 * <p>
 * 首次生成：原始需求 + 本轮搜到的图片。
 * 修改模式：先声明「改已有站点」，再附文件清单，要求局部修改而不是整站重写。
 * <p>
 * 总长上限 3000：需求/修改说明不截断；文件清单按完整路径行装入剩余预算；
 * 素材按 Logo → 架构图 → 插画 → 内容图优先，整条装入，放不下就跳过该条再试下一条。
 */
@Slf4j
public class PromptEnhancerNode {

    /** 增强后提示词总字数上限，对齐输入护栏（护栏 3100，这里留一点余量） */
    private static final int MAX_ENHANCED_CHARS = 3000;
    /** 单条素材描述上限，超长截断保留前缀，不整段丢弃 */
    private static final int MAX_IMAGE_DESC_CHARS = 60;

    /** 素材装入顺序：数量少、对站点更关键的类别优先 */
    private static final List<ImageCategoryEnum> IMAGE_PRIORITY = List.of(
            ImageCategoryEnum.LOGO,
            ImageCategoryEnum.ARCHITECTURE,
            ImageCategoryEnum.ILLUSTRATION,
            ImageCategoryEnum.CONTENT
    );

    private static final String EDIT_SUFFIX = """
            
            修改要求：
            1. 只改与本轮需求相关的文件，保持现有结构、命名和视觉风格。
            2. Vue 工程用文件工具读写，路径一律用相对路径（如 src/App.vue），不要使用绝对磁盘路径；HTML/多文件在原页面上改，不要换成另一个网站。
            3. 不要删除用户没提到的功能、路由和素材。
            """;

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            log.info("执行节点: 提示词增强");
            String originalPrompt = StrUtil.nullToEmpty(context.getOriginalPrompt());
            boolean editMode = Boolean.TRUE.equals(context.getEditMode());
            StringBuilder enhancedPromptBuilder = new StringBuilder();

            // 修改模式：把「增量改」写进提示词，模型才会用读写工具而不是当新项目生成
            if (editMode) {
                enhancedPromptBuilder.append("【这是对已有网站的修改，不是从零生成新站点】\n");
                enhancedPromptBuilder.append("用户本轮修改要求：\n");
                enhancedPromptBuilder.append(originalPrompt).append('\n');
                int fileBudget = MAX_ENHANCED_CHARS - enhancedPromptBuilder.length() - EDIT_SUFFIX.length();
                if (fileBudget <= 0) {
                    log.warn("需求/修改说明已占满预算（当前 {} 字），已有文件清单无法附加",
                            enhancedPromptBuilder.length() + EDIT_SUFFIX.length());
                } else {
                    appendFileSummary(enhancedPromptBuilder, context.getExistingFileSummary(), fileBudget);
                }
                enhancedPromptBuilder.append(EDIT_SUFFIX);
            } else {
                enhancedPromptBuilder.append(originalPrompt);
            }

            appendImages(enhancedPromptBuilder, context.getImageList(), MAX_ENHANCED_CHARS);

            String enhancedPrompt = enhancedPromptBuilder.toString();
            if (enhancedPrompt.length() > MAX_ENHANCED_CHARS) {
                log.warn("增强提示词仍超过上限：{} / {} 字（需求区未截断，输入护栏可能拒绝）",
                        enhancedPrompt.length(), MAX_ENHANCED_CHARS);
            }
            context.setCurrentStep("提示词增强");
            context.setEnhancedPrompt(enhancedPrompt);
            log.info("提示词增强完成，editMode={}, 原始={} 字, 增强后={} 字",
                    editMode, originalPrompt.length(), enhancedPrompt.length());
            return WorkflowContext.saveContext(context);
        });
    }

    /**
     * 按完整路径行装入文件清单，放不下就停，并注明「共 N 个、仅列出前 M 个」。
     *
     * @param budget 本次最多允许追加的字符数（已为修改要求套话留出空间）
     */
    private static void appendFileSummary(StringBuilder sb, String summary, int budget) {
        if (budget <= 0 || StrUtil.isBlank(summary)) {
            return;
        }
        String header = "\n已有文件（请先读取相关文件再局部修改，禁止无故重写整个项目）：\n";
        List<String> files = splitFileLines(summary);
        if (files.isEmpty()) {
            return;
        }
        if (header.length() > budget) {
            log.warn("文件清单标题已超出剩余预算，已跳过清单");
            return;
        }

        StringBuilder full = new StringBuilder(header);
        for (String file : files) {
            full.append(file).append('\n');
        }
        if (full.length() <= budget) {
            sb.append(full);
            return;
        }

        StringBuilder body = new StringBuilder();
        int listed = 0;
        for (String file : files) {
            String line = file + "\n";
            String note = truncationNote(files.size(), listed + 1);
            if (header.length() + body.length() + line.length() + note.length() <= budget) {
                body.append(line);
                listed++;
            } else {
                break;
            }
        }
        if (listed == 0) {
            log.warn("文件清单剩余预算不足以放入完整路径行，已跳过");
            return;
        }
        sb.append(header).append(body).append(truncationNote(files.size(), listed));
        log.warn("已有文件清单已按预算整行裁剪：列出 {}/{}", listed, files.size());
    }

    private static void appendImages(StringBuilder sb, List<ImageResource> imageList, int maxChars) {
        if (CollUtil.isEmpty(imageList)) {
            return;
        }
        List<ImageResource> ordered = imageList.stream()
                .sorted(Comparator.comparingInt(PromptEnhancerNode::imagePriority))
                .toList();

        final String sectionHead = "\n\n## 可用素材资源\n请在网站中合理使用以下图片资源。";
        int used = sb.length();
        int appendedImages = 0;
        int droppedByBudget = 0;
        int invalidImages = 0;
        for (ImageResource image : ordered) {
            if (image == null || StrUtil.isBlank(image.getUrl())) {
                invalidImages++;
                continue;
            }
            String line = formatImageLine(image);
            int headLen = appendedImages == 0 ? sectionHead.length() : 0;
            int addLen = headLen + 1 + line.length();
            if (used + addLen > maxChars) {
                droppedByBudget++;
                continue;
            }
            if (appendedImages == 0) {
                sb.append(sectionHead);
            }
            sb.append('\n').append(line);
            used += addLen;
            appendedImages++;
        }
        if (droppedByBudget > 0) {
            log.warn("素材预算不足：已附 {} 条、因预算整条跳过 {} 条（需求/修改说明保持完整）",
                    appendedImages, droppedByBudget);
        }
        if (invalidImages > 0) {
            log.debug("跳过 {} 条无效素材（无 URL）", invalidImages);
        }
    }

    private static String formatImageLine(ImageResource image) {
        String category = image.getCategory() == null ? "素材"
                : StrUtil.blankToDefault(image.getCategory().getText(), "素材");
        String desc = StrUtil.blankToDefault(image.getDescription(), "").trim();
        if (desc.length() > MAX_IMAGE_DESC_CHARS) {
            desc = desc.substring(0, MAX_IMAGE_DESC_CHARS - 1) + "…";
        }
        String descSuffix = desc.isEmpty() ? "" : "（" + desc + "）";
        return "- " + category + "：" + image.getUrl() + descSuffix;
    }

    private static int imagePriority(ImageResource image) {
        if (image == null || image.getCategory() == null) {
            return IMAGE_PRIORITY.size();
        }
        int idx = IMAGE_PRIORITY.indexOf(image.getCategory());
        return idx < 0 ? IMAGE_PRIORITY.size() : idx;
    }

    private static List<String> splitFileLines(String summary) {
        List<String> files = new ArrayList<>();
        for (String line : summary.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                files.add(trimmed);
            }
        }
        return files;
    }

    private static String truncationNote(int total, int listed) {
        return "（共 " + total + " 个文件，仅列出前 " + listed + " 个）\n";
    }
}

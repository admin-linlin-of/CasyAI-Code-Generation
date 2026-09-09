package com.casy.casyaicodemother.ai.tools;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;

/**
 * 工具基类
 * 定义所有工具的通用接口
 */
public abstract class BaseTool {

    /**
     * 获取工具的英文名称（对应方法名）
     *
     * @return 工具英文名称
     */
    public abstract String getToolName();

    /**
     * 获取工具的中文显示名称
     *
     * @return 工具中文名称
     */
    public abstract String getDisplayName();

    /**
     * 生成工具请求时的返回值（显示给用户）
     *
     * @return 工具请求显示内容
     */
    public String generateToolRequestResponse() {
        return String.format("\n\n[选择工具] %s\n\n", getDisplayName());
    }

    /**
     * 生成工具执行结果格式（保存到数据库）
     *
     * @param arguments 工具执行参数
     * @return 格式化的工具执行结果
     */
    public abstract String generateToolExecutedResult(JSONObject arguments);

    /**
     * 校验必填参数 relativeFilePath（所有文件类工具共用的入口校验）。
     * <p>
     * 背景：LLM 偶尔会返回“缺少 relativeFilePath”的工具调用（参数 JSON 合法但缺字段）。
     * 若不校验，relativeFilePath=null 会进入 {@code Paths.get(null)}，抛出无任何有效信息的
     * {@link NullPointerException}，langchain4j 会把裸异常类名回传给模型（如
     * {@code java.lang.NullPointerException}），模型无法定位缺哪个参数，只会重复同样的错误调用。
     * <p>
     * 因此这里改为抛出带明确指引的 {@link IllegalArgumentException}，langchain4j 会将其作为
     * “工具执行失败”回传模型（content 即本提示），帮助模型自行修正；前端也能显示可读的失败原因。
     */
    protected void requireRelativeFilePath(String relativeFilePath) {
        if (StrUtil.isBlank(relativeFilePath)) {
            throw new IllegalArgumentException(
                    getToolName() + " 工具缺少必填参数 relativeFilePath（目标文件/目录的相对路径），"
                            + "本次调用未执行任何文件操作。请补上该参数后重新调用，"
                            + "例如 relativeFilePath=\"src/App.vue\"；分块写入时每一个分块都必须携带相同的 relativeFilePath。");
        }
    }
}

package com.casy.casyaicodemother.ai.tools;

import cn.hutool.json.JSONObject;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;

/**
 * 为 AI 提供一个专门地退出工具，让它能够主动结束工具调用循环
 * 这种方案的优势在于给了 AI 更多的主动权。AI 可以根据任务完成情况主动判断是否需要继续调用工具，而不是被动地等待达到调用次数上限
 * 退出工具的效果其实在自主实现的多步骤智能体中效果更好。
 * 所以我建议如果你的应用中很少出现工具调用循环的问题，可以只使用方案 1。
 * 因为每多一个工具都会增加系统的复杂性和不稳定性
 *
 */
@Slf4j
//@Component // 没有出显示循环调用的问题，暂时注释掉
public class ExitTool extends BaseTool {

    @Override
    public String getToolName() {
        return "exit";
    }

    @Override
    public String getDisplayName() {
        return "退出工具调用";
    }

    /**
     * 退出工具调用
     * 当任务完成或无需继续使用工具时调用此方法
     *
     * @return 退出确认信息
     */
    @Tool("当任务已完成或无需继续调用工具时，使用此工具退出操作，防止循环")
    public String exit() {
        log.info("AI 请求退出工具调用");
        return "不要继续调用工具，可以输出最终结果了";
    }

    @Override
    public String generateToolExecutedResult(JSONObject arguments) {
        return "\n\n[执行结束]\n\n";
    }
}

package com.casy.casyaicodemother.constant;

public interface ChatHistoryConstant {

    /**
     * 游标查询，默认页大小
     */
    int DEFAULT_PAGE_SIZE = 10;

    /**
     * 游标查询，最大页大小
     */
    int MAX_PAGE_SIZE = 50;

    /**
     * 单条消息落库前的防御性字节上限（UTF-8）。
     * Vue 流式生成会把整轮“深度思考 + 工具标签”合成一条 ai 消息，可能超过 MySQL TEXT 的 64KB。
     * 该值保证即使字段仍是 TEXT 也不会抛 Data truncation；
     * 若已把 message 升级为 MEDIUMTEXT（sql/upgrade_t_chat_history_message_to_mediumtext.sql）且想保留
     * 更长的完整思考内容，可调大该值（上限建议 8MB 以内）。
     */
    int MAX_MESSAGE_SAFE_BYTES = 60_000;
}

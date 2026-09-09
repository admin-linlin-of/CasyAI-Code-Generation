-- 升级脚本：t_chat_history.message TEXT -> MEDIUMTEXT
-- 背景：Vue 工程流式生成收尾时会把整轮“深度思考(<aiThinking>) + 正文 + 工具标签(<fileWrite>…)”
-- 合成一条 ai 消息写入 t_chat_history。开启思考后该内容经常超过 MySQL TEXT 的 65,535 字节上限，
-- 报错：Data truncation: Data too long for column 'message' at row 1。
--
-- MySQL 执行：
--   ALTER TABLE t_chat_history MODIFY COLUMN message MEDIUMTEXT NOT NULL COMMENT '消息';
-- 验证：
--   SHOW COLUMNS FROM t_chat_history LIKE 'message';   -- 期望 Type = mediumtext
--
-- PostgreSQL 的 TEXT 无长度上限，无需改动。
ALTER TABLE t_chat_history
    MODIFY COLUMN message MEDIUMTEXT NOT NULL COMMENT '消息（深度思考+正文+工具标签可能很长，须大于 64KB）';

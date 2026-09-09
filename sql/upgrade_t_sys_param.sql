CREATE TABLE IF NOT EXISTS t_sys_param
(
    id          BIGINT        NOT NULL PRIMARY KEY COMMENT 'id',
    param_key   VARCHAR(128)  NOT NULL COMMENT '参数键，如 site.github.url',
    param_value VARCHAR(1024) NULL COMMENT '参数值',
    param_name  VARCHAR(128)  NOT NULL COMMENT '展示名称',
    remark      VARCHAR(512)  NULL COMMENT '备注',
    enabled     TINYINT       NOT NULL DEFAULT 1 COMMENT '1 启用，0 停用',
    is_public   TINYINT       NOT NULL DEFAULT 0 COMMENT '1 未登录可读取',
    sort_order  INT           NOT NULL DEFAULT 100 COMMENT '排序，越小越靠前',
    create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete   TINYINT       NOT NULL DEFAULT 0 COMMENT '是否删除',
    UNIQUE KEY uk_sys_param_key (param_key)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '系统参数';

INSERT IGNORE INTO t_sys_param (id, param_key, param_value, param_name, remark, enabled, is_public, sort_order)
VALUES
    (1, 'site.github.url', '', 'GitHub 仓库地址', '首页左上角 GitHub 图标跳转地址，留空则不显示图标', 1, 1, 10),
    (2, 'site.gitee.url', 'https://gitee.com/linlinyes/casy-ai-code-mother', 'Gitee 仓库地址',
     '首页左上角 Gitee 图标跳转地址，留空则不显示图标', 1, 1, 20),
    (3, 'login.demo.account', 'user01', '登录页体验账号',
     '展示在登录页的默认账号，修改后刷新登录页生效。实际登录仍以用户表为准', 1, 1, 30),
    (4, 'login.demo.password', '1234567', '登录页体验密码',
     '展示在登录页的默认密码。若修改此项，请同步改用户表中对应账号的密码', 1, 1, 40);

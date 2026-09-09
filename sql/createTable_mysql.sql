CREATE DATABASE IF NOT EXISTS casy_code DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE casy_code;

CREATE TABLE IF NOT EXISTS t_user
(
    id              BIGINT       NOT NULL PRIMARY KEY COMMENT '主键ID',
    user_account    VARCHAR(256) NOT NULL COMMENT '用户账号',
    user_password   VARCHAR(512) NOT NULL COMMENT '用户密码',
    user_name       VARCHAR(256) NULL COMMENT '用户昵称',
    user_avatar     VARCHAR(1024) NULL COMMENT '用户头像',
    user_profile    VARCHAR(512) NULL COMMENT '用户简介',
    user_role       VARCHAR(256) NOT NULL DEFAULT 'user' COMMENT '用户角色：user/admin',
    vip_expire_time DATETIME     NULL COMMENT '会员过期时间',
    vip_code        VARCHAR(128) NULL COMMENT '会员兑换码',
    vip_number      BIGINT       NULL COMMENT '会员编号',
    share_code      VARCHAR(20)  DEFAULT NULL COMMENT '分享码',
    invite_user     BIGINT        DEFAULT NULL COMMENT '邀请用户ID',
    edit_time       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '编辑时间',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete       TINYINT     NOT NULL DEFAULT 0 COMMENT '是否删除 0-未删除 1-已删除',
    UNIQUE KEY uk_user_account (user_account),
    KEY idx_user_name (user_name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '用户表';

CREATE TABLE IF NOT EXISTS t_app
(
    id            BIGINT       NOT NULL PRIMARY KEY COMMENT 'id',
    app_name      VARCHAR(256) NULL COMMENT '应用名称',
    cover         VARCHAR(512) NULL COMMENT '应用封面',
    init_prompt   TEXT         NULL COMMENT '应用初始化的 prompt',
    code_gen_type VARCHAR(64)  NULL COMMENT '代码生成类型（枚举）',
    deploy_key    VARCHAR(64)  NULL COMMENT '部署标识',
    deployed_time DATETIME     NULL COMMENT '部署时间',
    priority      INT          NOT NULL DEFAULT 0 COMMENT '优先级',
    user_id       BIGINT       NOT NULL COMMENT '创建用户id',
    app_types     JSON         NOT NULL DEFAULT (JSON_ARRAY()) COMMENT '应用类型数组，JSON格式，每个元素为字符串类型的应用类型标识',
    is_publish    TINYINT      NOT NULL DEFAULT 0 COMMENT '是否公布应用，0不公布，1公布',
    edit_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '编辑时间',
    create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete     TINYINT      NOT NULL DEFAULT 0 COMMENT '是否删除',
    UNIQUE KEY uk_deploy_key (deploy_key),
    KEY idx_app_name (app_name),
    KEY idx_user_id (user_id),
    KEY idx_app_is_publish (is_publish)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '应用';

CREATE TABLE IF NOT EXISTS t_chat_history
(
    id           BIGINT      NOT NULL PRIMARY KEY COMMENT 'id',
    message      MEDIUMTEXT  NOT NULL COMMENT '消息（深度思考+正文+工具标签可能很长，须大于 64KB）',
    message_type VARCHAR(32) NOT NULL COMMENT 'user/ai',
    app_id       BIGINT      NOT NULL COMMENT '应用id',
    user_id      BIGINT      NOT NULL COMMENT '创建用户id',
    parent_id    BIGINT      NULL COMMENT '父消息id',
    create_time  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete    TINYINT     NOT NULL DEFAULT 0 COMMENT '是否删除',
    KEY idx_app_id (app_id),
    KEY idx_create_time (create_time),
    KEY idx_app_id_create_time (app_id, create_time)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '对话历史';

CREATE TABLE IF NOT EXISTS t_app_version
(
    id              BIGINT       NOT NULL PRIMARY KEY COMMENT 'id',
    app_id          BIGINT       NOT NULL COMMENT '应用id',
    chat_history_id BIGINT       NULL COMMENT '关联的AI对话消息id',
    version_num     INT          NOT NULL COMMENT '版本号，从1递增',
    code_dir        VARCHAR(512) NOT NULL COMMENT '代码目录，如 v1、v2',
    model_type      VARCHAR(64)  NULL COMMENT '生成该版本使用的AI模型',
    build_status    VARCHAR(32)  NOT NULL DEFAULT 'pending' COMMENT '构建状态：pending/building/success/failed',
    build_error     TEXT         NULL COMMENT '构建失败原因（npm 输出摘要）',
    deploy_status   VARCHAR(32)  NOT NULL DEFAULT 'not_deployed' COMMENT '部署状态：not_deployed/deploying/success/failed',
    user_id         BIGINT       NOT NULL COMMENT '创建用户id',
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete       TINYINT      NOT NULL DEFAULT 0 COMMENT '是否删除',
    UNIQUE KEY uk_app_version (app_id, version_num),
    KEY idx_app_version_app_id (app_id),
    KEY idx_app_version_chat_history_id (chat_history_id),
    KEY idx_app_version_app_id_create_time (app_id, create_time)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '应用代码版本';

CREATE TABLE IF NOT EXISTS t_ai_model
(
    id           BIGINT       NOT NULL PRIMARY KEY COMMENT 'id',
    model_code   VARCHAR(64)  NOT NULL COMMENT '与 ModelTypeEnum 枚举名一致，如 GPT、DEEPSEEKFLASH',
    model_name   VARCHAR(128) NOT NULL COMMENT '实际调用名，如 gpt-5.5',
    enabled      TINYINT      NOT NULL DEFAULT 1 COMMENT '1 可用，0 停用',
    is_default    TINYINT      NOT NULL DEFAULT 0 COMMENT '路由失败或选中已停用模型时的回退，启用中应只有一条为 1',
    sort_order   INT          NOT NULL DEFAULT 100 COMMENT '越小越便宜，多条件命中时按此优先',
    description  TEXT         NULL COMMENT '写入路由 prompt 的模型说明',
    routing_rule TEXT         NULL COMMENT '写入路由 prompt 的选择规则',
    create_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete     TINYINT      NOT NULL DEFAULT 0 COMMENT '是否删除',
    UNIQUE KEY uk_ai_model_code (model_code)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '可用 AI 模型目录，enabled=0 时路由和指定模型都会跳过';

INSERT IGNORE INTO t_ai_model (id, model_code, model_name, enabled, is_default, sort_order, description, routing_rule)
VALUES
    (1, 'DEEPSEEKFLASH', 'deepseek-v4-flash', 1, 1, 10,
     '默认高性价比模型。适合简单、明确、低风险的需求，例如静态页面、登录页、展示页、小组件、样式微调、简单表单、少量原生 JavaScript 交互。',
     '如果用户需求简单、边界清晰、只需要快速生成普通页面或小功能，选择 DEEPSEEKFLASH。'),
    (2, 'DEEPSEEKPRO', 'deepseek-v4-pro', 1, 0, 20,
     '强推理模型。适合中高复杂度需求，例如复杂业务逻辑、多状态交互、数据处理、代码重构、Bug 修复、性能优化、需要更强逻辑推理或更稳定工程质量的任务。',
     '如果用户需求包含算法、复杂条件分支、状态管理、异步流程、数据校验、错误处理、性能优化、复杂 Bug 排查，选择 DEEPSEEKPRO。'),
    (3, 'GPT', 'gpt-5.5', 0, 0, 30,
     '通用强能力模型。适合需要较好综合能力、产品理解、创意设计、自然语言表达、前端体验打磨、复杂但不极端的 Web 应用生成任务。',
     '如果用户强调视觉效果、交互体验、产品感、文案质量、页面创意，或需求属于中等复杂度的完整应用，选择 GPT。'),
    (4, 'CLAUDESONNET', 'claude-sonnet-4-6', 1, 0, 40,
     '长上下文与复杂工程模型。适合大型 Vue 项目、多文件项目、跨模块修改、架构设计、复杂组件协作、长需求说明、需要保持上下文一致性和代码可维护性的任务。',
     '如果用户需求涉及 Vue 项目、多个页面、多个组件、路由、状态管理、复杂文件结构、持续迭代已有项目、长上下文理解，选择 CLAUDESONNET。');

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
     '首页左上角 Gitee 图标跳转地址，留空则不显示图标', 1, 1, 20);

CREATE TABLE IF NOT EXISTS t_guardrail_event
(
    id            BIGINT       NOT NULL PRIMARY KEY COMMENT 'id',
    user_id       BIGINT       NULL COMMENT '用户ID',
    user_account  VARCHAR(256) NULL COMMENT '用户账号',
    user_name     VARCHAR(256) NULL COMMENT '用户昵称',
    app_id        BIGINT       NULL COMMENT '应用ID',
    request_uri   VARCHAR(512) NULL COMMENT '请求路径',
    request_ip    VARCHAR(64)  NULL COMMENT '客户端IP',
    input_content TEXT         NULL COMMENT '被拦截的输入内容',
    rule_type     VARCHAR(64)  NOT NULL COMMENT '触发规则：LENGTH/EMPTY/SENSITIVE_WORD/INJECTION',
    rule_detail   VARCHAR(512) NULL COMMENT '命中的敏感词或正则',
    fail_message  VARCHAR(512) NULL COMMENT '返回给前端的错误信息',
    handle_result VARCHAR(64)  NOT NULL COMMENT '处理结果：SSE_PUSHED/JSON_RETURNED',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete     TINYINT      NOT NULL DEFAULT 0 COMMENT '是否删除',
    KEY idx_guardrail_event_user_id (user_id),
    KEY idx_guardrail_event_rule_type (rule_type),
    KEY idx_guardrail_event_create_time (create_time)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '输入护轨拦截事件';

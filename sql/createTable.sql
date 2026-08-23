-- 1. 创建自动更新时间的触发器函数（蛇形命名）
CREATE OR REPLACE FUNCTION update_modified_column()
    RETURNS TRIGGER AS $$
BEGIN
    NEW.update_time = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 2. 创建用户表（全小写+蛇形命名，移除双引号，表名改为 t_user 避免关键字冲突）
CREATE TABLE IF NOT EXISTS t_user
(
    id              BIGINT NOT NULL PRIMARY KEY,
    user_account    VARCHAR(256) NOT NULL,
    user_password   VARCHAR(512) NOT NULL,
    user_name       VARCHAR(256) NULL,
    user_avatar     VARCHAR(1024) NULL,
    user_profile    VARCHAR(512) NULL,
    user_role       VARCHAR(256) NOT NULL DEFAULT 'user',
    vip_expire_time TIMESTAMP NULL,
    vip_code        VARCHAR(128) NULL,
    vip_number      BIGINT NULL,
    share_code      VARCHAR(20) DEFAULT NULL,
    invite_user     BIGINT DEFAULT NULL,
    edit_time       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_time     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_delete       SMALLINT NOT NULL DEFAULT 0,
    -- 唯一约束（蛇形命名）
    CONSTRAINT uk_user_account UNIQUE (user_account)
);

-- 3. 创建索引（蛇形命名）
DROP INDEX IF EXISTS idx_user_name;
CREATE INDEX idx_user_name ON t_user (user_name);

-- 4. 绑定更新时间触发器
CREATE TRIGGER update_user_modtime
    BEFORE UPDATE ON t_user
    FOR EACH ROW
EXECUTE FUNCTION update_modified_column();

-- 5. 表+字段注释（对应蛇形列名）
COMMENT ON TABLE t_user IS '用户表';
COMMENT ON COLUMN t_user.id IS '主键ID';
COMMENT ON COLUMN t_user.user_account IS '用户账号';
COMMENT ON COLUMN t_user.user_password IS '用户密码';
COMMENT ON COLUMN t_user.user_name IS '用户昵称';
COMMENT ON COLUMN t_user.user_avatar IS '用户头像';
COMMENT ON COLUMN t_user.user_profile IS '用户简介';
COMMENT ON COLUMN t_user.user_role IS '用户角色：user/admin';
COMMENT ON COLUMN t_user.vip_expire_time IS '会员过期时间';
COMMENT ON COLUMN t_user.vip_code IS '会员兑换码';
COMMENT ON COLUMN t_user.vip_number IS '会员编号';
COMMENT ON COLUMN t_user.share_code IS '分享码';
COMMENT ON COLUMN t_user.invite_user IS '邀请用户ID';
COMMENT ON COLUMN t_user.edit_time IS '编辑时间';
COMMENT ON COLUMN t_user.create_time IS '创建时间';
COMMENT ON COLUMN t_user.update_time IS '更新时间';
COMMENT ON COLUMN t_user.is_delete IS '是否删除 0-未删除 1-已删除';


-- 应用表
CREATE TABLE IF NOT EXISTS t_app
(
    id              BIGSERIAL PRIMARY KEY,
    app_name        VARCHAR(256) NULL,
    cover           VARCHAR(512) NULL,
    init_prompt     TEXT NULL,
    code_gen_type   VARCHAR(64) NULL,
    deploy_key      VARCHAR(64) NULL,
    deployed_time   TIMESTAMP NULL,
    -- priority 优先级字段：我们约定 99 表示精选应用，这样可以在主页展示高质量的应用，避免用户看到大量测试内容。
    -- 为什么用数字‍‍而不用枚举类型呢？原因是这样更利于扩展，比如约定 999 表示置顶；还可以根据数字灵活调整各个应用的具体展示顺序。
    priority        INT NOT NULL DEFAULT 0,
    user_id         BIGINT NOT NULL,
    edit_time       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_time     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_delete       SMALLINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_deploy_key UNIQUE (deploy_key)
);

CREATE INDEX IF NOT EXISTS idx_app_name ON t_app (app_name);
CREATE INDEX IF NOT EXISTS idx_user_id ON t_app (user_id);

CREATE TRIGGER update_app_modtime
    BEFORE UPDATE ON t_app
    FOR EACH ROW
    EXECUTE FUNCTION update_modified_column();

COMMENT ON TABLE t_app IS '应用';
COMMENT ON COLUMN t_app.id IS 'id';
COMMENT ON COLUMN t_app.app_name IS '应用名称';
COMMENT ON COLUMN t_app.cover IS '应用封面';
COMMENT ON COLUMN t_app.init_prompt IS '应用初始化的 prompt';
COMMENT ON COLUMN t_app.code_gen_type IS '代码生成类型（枚举）';
COMMENT ON COLUMN t_app.deploy_key IS '部署标识';
COMMENT ON COLUMN t_app.deployed_time IS '部署时间';
COMMENT ON COLUMN t_app.priority IS '优先级';
COMMENT ON COLUMN t_app.user_id IS '创建用户id';
COMMENT ON COLUMN t_app.edit_time IS '编辑时间';
COMMENT ON COLUMN t_app.create_time IS '创建时间';
COMMENT ON COLUMN t_app.update_time IS '更新时间';
COMMENT ON COLUMN t_app.is_delete IS '是否删除';


-- 1. 添加字段（jsonb 类型，默认空数组，非空约束）
ALTER TABLE "t_app"
    ADD COLUMN IF NOT EXISTS app_types jsonb NOT NULL DEFAULT '[]'::jsonb;

-- 2. 添加字段注释（说明用途）
COMMENT ON COLUMN "t_app".app_types IS '应用类型数组，JSON格式，每个元素为字符串类型的应用类型标识';

-- 创建 GIN 索引，支持 jsonb 数组的包含查询
CREATE INDEX idx_app_apptypes ON "t_app" USING GIN (app_types);


-- 1. 添加字段
ALTER TABLE "t_app"
    ADD COLUMN IF NOT EXISTS is_publish SMALLINT NOT NULL DEFAULT 0;

-- 2. 添加字段注释（说明用途）
COMMENT ON COLUMN "t_app".is_publish IS '是否公布应用，0不公布，1公布';

-- 创建 is_publish 的索引
CREATE INDEX IF NOT EXISTS idx_app_is_publish ON t_app (is_publish);

-- 对话历史表
CREATE TABLE t_chat_history
(
    id           BIGSERIAL PRIMARY KEY,
    message      TEXT         NOT NULL,
    message_type VARCHAR(32)  NOT NULL,
    app_id       BIGINT       NOT NULL,
    user_id      BIGINT       NOT NULL,
    parent_id    BIGINT           NULL,
    create_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_delete    SMALLINT     NOT NULL DEFAULT 0
);

CREATE INDEX idx_app_id ON t_chat_history (app_id);
CREATE INDEX idx_create_time ON t_chat_history (create_time);
CREATE INDEX idx_app_id_create_time ON t_chat_history (app_id, create_time);

CREATE OR REPLACE FUNCTION update_t_chat_history_update_time()
    RETURNS TRIGGER AS $$
BEGIN
    NEW.update_time = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_t_chat_history_update_time
    BEFORE UPDATE ON t_chat_history
    FOR EACH ROW
EXECUTE FUNCTION update_t_chat_history_update_time();

COMMENT ON TABLE t_chat_history IS '对话历史';
COMMENT ON COLUMN t_chat_history.id IS 'id';
COMMENT ON COLUMN t_chat_history.message IS '消息';
COMMENT ON COLUMN t_chat_history.message_type IS 'user/ai';
COMMENT ON COLUMN t_chat_history.app_id IS '应用id';
COMMENT ON COLUMN t_chat_history.user_id IS '创建用户id';
COMMENT ON COLUMN t_chat_history.create_time IS '创建时间';
COMMENT ON COLUMN t_chat_history.update_time IS '更新时间';
COMMENT ON COLUMN t_chat_history.is_delete IS '是否删除';
-- TODO 可以按需添加 parentId 字段，将 AI 消息和对应的用户提示词进行关联，便于生成失败时的重试、或者用户手动重新生成,我不太理解这个意思，不过应该是涉及重试的逻辑，之后在实现
COMMENT ON COLUMN t_chat_history.parent_id IS '父消息id';

drop table t_app_version;

-- 应用代码版本表
CREATE TABLE IF NOT EXISTS t_app_version
(
    id              BIGSERIAL PRIMARY KEY,
    app_id          BIGINT       NOT NULL,
    chat_history_id BIGINT           NULL,
    version_num     INT          NOT NULL,
    code_dir        VARCHAR(512) NOT NULL,
    model_type      VARCHAR(64)      NULL,
    build_status    VARCHAR(32)  NOT NULL DEFAULT 'pending',
    build_error     TEXT             NULL,
    deploy_status   VARCHAR(32)  NOT NULL DEFAULT 'not_deployed',
    user_id         BIGINT       NOT NULL,
    create_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_delete       SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT uk_app_version UNIQUE (app_id, version_num)
);

CREATE INDEX IF NOT EXISTS idx_app_version_app_id ON t_app_version (app_id);
CREATE INDEX IF NOT EXISTS idx_app_version_chat_history_id ON t_app_version (chat_history_id);
CREATE INDEX IF NOT EXISTS idx_app_version_app_id_create_time ON t_app_version (app_id, create_time);

CREATE TRIGGER update_app_version_modtime
    BEFORE UPDATE ON t_app_version
    FOR EACH ROW
    EXECUTE FUNCTION update_modified_column();

COMMENT ON TABLE t_app_version IS '应用代码版本';
COMMENT ON COLUMN t_app_version.id IS 'id';
COMMENT ON COLUMN t_app_version.app_id IS '应用id';
COMMENT ON COLUMN t_app_version.chat_history_id IS '关联的AI对话消息id';
COMMENT ON COLUMN t_app_version.version_num IS '版本号，从1递增';
COMMENT ON COLUMN t_app_version.code_dir IS '代码目录，如 v1、v2';
COMMENT ON COLUMN t_app_version.model_type IS '生成该版本使用的AI模型';
COMMENT ON COLUMN t_app_version.build_status IS '构建状态：pending/building/success/failed';
COMMENT ON COLUMN t_app_version.build_error IS '构建失败原因（npm 输出摘要）';
COMMENT ON COLUMN t_app_version.deploy_status IS '部署状态：not_deployed/deploying/success/failed';
COMMENT ON COLUMN t_app_version.user_id IS '创建用户id';
COMMENT ON COLUMN t_app_version.create_time IS '创建时间';
COMMENT ON COLUMN t_app_version.update_time IS '更新时间';
COMMENT ON COLUMN t_app_version.is_delete IS '是否删除';

ALTER TABLE t_app_version ADD COLUMN IF NOT EXISTS build_status VARCHAR(32) NOT NULL DEFAULT 'pending';
ALTER TABLE t_app_version ADD COLUMN IF NOT EXISTS build_error TEXT NULL;
ALTER TABLE t_app_version ALTER COLUMN build_error TYPE TEXT;
ALTER TABLE t_app_version ADD COLUMN IF NOT EXISTS deploy_status VARCHAR(32) NOT NULL DEFAULT 'not_deployed';
COMMENT ON COLUMN t_app_version.build_status IS '构建状态：pending/building/success/failed';
COMMENT ON COLUMN t_app_version.build_error IS '构建失败原因（npm 输出摘要）';
COMMENT ON COLUMN t_app_version.deploy_status IS '部署状态：not_deployed/deploying/success/failed';

CREATE TABLE IF NOT EXISTS t_ai_model
(
    id            BIGINT       NOT NULL PRIMARY KEY,
    model_code    VARCHAR(64)  NOT NULL,
    model_name    VARCHAR(128) NOT NULL,
    enabled       SMALLINT     NOT NULL DEFAULT 1,
    is_default    SMALLINT     NOT NULL DEFAULT 0,
    sort_order    INT          NOT NULL DEFAULT 100,
    description   TEXT         NULL,
    routing_rule  TEXT         NULL,
    create_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_delete     SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT uk_ai_model_code UNIQUE (model_code)
);

CREATE TRIGGER update_ai_model_modtime
    BEFORE UPDATE ON t_ai_model
    FOR EACH ROW
EXECUTE FUNCTION update_modified_column();

COMMENT ON TABLE t_ai_model IS '可用 AI 模型目录，enabled=0 时路由和指定模型都会跳过';
COMMENT ON COLUMN t_ai_model.model_code IS '与 ModelTypeEnum 枚举名一致，如 GPT、DEEPSEEKFLASH';
COMMENT ON COLUMN t_ai_model.model_name IS '实际调用名，如 gpt-5.5';
COMMENT ON COLUMN t_ai_model.enabled IS '1 可用，0 停用';
COMMENT ON COLUMN t_ai_model.is_default IS '路由失败或选中已停用模型时的回退，启用中应只有一条为 1';
COMMENT ON COLUMN t_ai_model.sort_order IS '越小越便宜，多条件命中时按此优先';
COMMENT ON COLUMN t_ai_model.description IS '写入路由 prompt 的模型说明';
COMMENT ON COLUMN t_ai_model.routing_rule IS '写入路由 prompt 的选择规则';

INSERT INTO t_ai_model (id, model_code, model_name, enabled, is_default, sort_order, description, routing_rule)
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
     '如果用户需求涉及 Vue 项目、多个页面、多个组件、路由、状态管理、复杂文件结构、持续迭代已有项目、长上下文理解，选择 CLAUDESONNET。')
ON CONFLICT (model_code) DO NOTHING;


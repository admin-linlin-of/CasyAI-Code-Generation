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
COMMENT ON COLUMN t_app_version.deploy_status IS '部署状态：not_deployed/deploying/success/failed';
COMMENT ON COLUMN t_app_version.user_id IS '创建用户id';
COMMENT ON COLUMN t_app_version.create_time IS '创建时间';
COMMENT ON COLUMN t_app_version.update_time IS '更新时间';
COMMENT ON COLUMN t_app_version.is_delete IS '是否删除';

ALTER TABLE t_app_version ADD COLUMN IF NOT EXISTS build_status VARCHAR(32) NOT NULL DEFAULT 'pending';
ALTER TABLE t_app_version ADD COLUMN IF NOT EXISTS deploy_status VARCHAR(32) NOT NULL DEFAULT 'not_deployed';
COMMENT ON COLUMN t_app_version.build_status IS '构建状态：pending/building/success/failed';
COMMENT ON COLUMN t_app_version.deploy_status IS '部署状态：not_deployed/deploying/success/failed';

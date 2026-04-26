-- 1. 创建自动更新时间的触发器函数
CREATE OR REPLACE FUNCTION update_modified_column()
    RETURNS TRIGGER AS $$
BEGIN
    NEW."updateTime" = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 2. 创建用户表（移除表内普通索引，仅保留约束）
CREATE TABLE IF NOT EXISTS "user"
(
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    "userAccount"  VARCHAR(256) NOT NULL,
    "userPassword" VARCHAR(512) NOT NULL,
    "userName"     VARCHAR(256) NULL,
    "userAvatar"   VARCHAR(1024) NULL,
    "userProfile"  VARCHAR(512) NULL,
    "userRole"     VARCHAR(256) NOT NULL DEFAULT 'user',
    "vipExpireTime" TIMESTAMP NULL,
    "vipCode"       VARCHAR(128) NULL,
    "vipNumber"     BIGINT NULL,
    "shareCode"     VARCHAR(20) DEFAULT NULL,
    "inviteUser"    BIGINT DEFAULT NULL,
    "editTime"     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "createTime"   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updateTime"   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "isDelete"     SMALLINT NOT NULL DEFAULT 0,
    -- 唯一约束（PG 支持表内定义）
    CONSTRAINT uk_userAccount UNIQUE ("userAccount")
);

-- 3. 单独创建普通索引（PostgreSQL 标准语法）
CREATE INDEX idx_userName ON "user" ("userName");

-- 4. 绑定更新时间触发器
CREATE TRIGGER update_user_modtime
    BEFORE UPDATE ON "user"
    FOR EACH ROW
EXECUTE FUNCTION update_modified_column();

-- 5. 添加表 + 所有字段注释
COMMENT ON TABLE "user" IS '用户';
COMMENT ON COLUMN "user".id IS 'id';
COMMENT ON COLUMN "user"."userAccount" IS '账号';
COMMENT ON COLUMN "user"."userPassword" IS '密码';
COMMENT ON COLUMN "user"."userName" IS '用户昵称';
COMMENT ON COLUMN "user"."userAvatar" IS '用户头像';
COMMENT ON COLUMN "user"."userProfile" IS '用户简介';
COMMENT ON COLUMN "user"."userRole" IS '用户角色：user/admin';
COMMENT ON COLUMN "user"."vipExpireTime" IS '会员过期时间';
COMMENT ON COLUMN "user"."vipCode" IS '会员兑换码';
COMMENT ON COLUMN "user"."vipNumber" IS '会员编号';
COMMENT ON COLUMN "user"."shareCode" IS '分享码';
COMMENT ON COLUMN "user"."inviteUser" IS '邀请用户 id';
COMMENT ON COLUMN "user"."editTime" IS '编辑时间';
COMMENT ON COLUMN "user"."createTime" IS '创建时间';
COMMENT ON COLUMN "user"."updateTime" IS '更新时间';
COMMENT ON COLUMN "user"."isDelete" IS '是否删除';
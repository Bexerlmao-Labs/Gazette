-- =====================================================================
-- 用户表：一个用户可以拥有多台墨水屏设备
-- =====================================================================
CREATE TABLE IF NOT EXISTS t_user
(
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL,
    password_hash VARCHAR(128) NOT NULL,
    nickname      VARCHAR(64),
    email         VARCHAR(128),
    phone         VARCHAR(32),
    status        SMALLINT     NOT NULL DEFAULT 1, -- 1:正常 0:禁用
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_user_username ON t_user (username);

COMMENT ON TABLE  t_user             IS '用户表';
COMMENT ON COLUMN t_user.username    IS '登录名，唯一';
COMMENT ON COLUMN t_user.password_hash IS '密码哈希（不存明文）';
COMMENT ON COLUMN t_user.nickname    IS '昵称';
COMMENT ON COLUMN t_user.status      IS '账号状态：1-正常，0-禁用';

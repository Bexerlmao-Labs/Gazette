-- =====================================================================
-- 外部数据源凭证表（见 docs/dynamic-content-design.md §4.1）
--
-- 设计原则：token 用量等业务数据一律【不落库】，
--   服务端渲染时实时向各平台拉取（GLM 账单接口 / DeepSeek 余额与平台用量接口），
--   仅保留 60s 内存缓存用于防频控；进程重启即失效，无历史数据。
--
-- 本表只存"访问凭证"：
--   - api_key            各平台 API Key（AES 加密，密钥 gazette.security.secret）
--   - auth_type          认证方式：api_key / platform_cookie / platform_password
--   - account/secret_enc 平台账号密码（⚠️ AES 加密存储，UI 明确警告后用户自担）
--   - cookie_enc         平台登录态 Cookie（AES 加密，失效后重新粘贴）
-- =====================================================================
CREATE TABLE IF NOT EXISTS t_data_source
(
    provider   VARCHAR(32) PRIMARY KEY,      -- 数据源标识：glm / deepseek
    api_key    VARCHAR(512),                 -- API Key（AES 加密存储）
    auth_type  VARCHAR(32) NOT NULL DEFAULT 'api_key',
    account    VARCHAR(128),                 -- 平台登录账号（手机号/邮箱，明文）
    secret_enc VARCHAR(512),                 -- 平台登录密码（AES 加密存储）
    cookie_enc TEXT,                         -- 平台登录态 Cookie（AES 加密存储）
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  t_data_source           IS '外部数据源凭证表（仅存凭证；用量数据实时拉取，不落库）';
COMMENT ON COLUMN t_data_source.provider   IS '数据源命名空间：glm / deepseek';
COMMENT ON COLUMN t_data_source.api_key    IS 'API Key（AES 加密存储）';
COMMENT ON COLUMN t_data_source.auth_type  IS '认证方式：api_key / platform_cookie / platform_password';
COMMENT ON COLUMN t_data_source.account    IS '平台登录账号（手机号/邮箱）';
COMMENT ON COLUMN t_data_source.secret_enc IS '平台登录密码（AES 加密）';
COMMENT ON COLUMN t_data_source.cookie_enc IS '平台登录态 Cookie（AES 加密，失效后重新粘贴）';

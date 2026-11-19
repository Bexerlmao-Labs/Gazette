-- =====================================================================
-- 画面场景表：用户编辑并保存的"原始画面"
-- 一台设备显示的是某个场景的最终渲染结果；场景归用户所有，可绑定到多台设备
--
-- 分层存储：
--   base_image  静态底图：用户绘制的原始画面（不含任何动态内容）
--   overlay_dsl 动态图层：行式 DSL，描述动态元素的位置/类型/样式/数据绑定
-- =====================================================================
CREATE TABLE IF NOT EXISTS t_scene
(
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    name        VARCHAR(64)  NOT NULL,               -- 场景名，如"书房天气面板"
    width       INT          NOT NULL DEFAULT 0,     -- 画布像素宽
    height      INT          NOT NULL DEFAULT 0,     -- 画布像素高
    base_image  BYTEA,                               -- 静态底图（PNG 或 1bpp 打包位图）
    base_hash   VARCHAR(64),                         -- 底图内容哈希
    overlay_dsl TEXT,                                -- 动态元素描述，见 docs/dynamic-content-design.md
    status      SMALLINT     NOT NULL DEFAULT 1,     -- 1:启用 0:已删除(软删)
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_scene_user ON t_scene (user_id, updated_at DESC);

ALTER TABLE t_scene
    ADD CONSTRAINT fk_scene_user FOREIGN KEY (user_id) REFERENCES t_user (id);

COMMENT ON TABLE  t_scene             IS '画面场景表：用户保存的原始画面 + 动态图层描述';
COMMENT ON COLUMN t_scene.user_id     IS '所属用户，一个用户可有多个场景，可复用到多台设备';
COMMENT ON COLUMN t_scene.base_image  IS '静态底图二进制，只含用户绘制的原始内容，不含动态数据';
COMMENT ON COLUMN t_scene.overlay_dsl IS '动态元素 DSL 文本（text/chart/datetime/qr...），渲染时叠加在底图上';

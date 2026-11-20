-- =====================================================================
-- 画面场景表：保存的"原始画面"
-- 一台设备显示的是某个场景的最终渲染结果；场景可绑定到多台设备
--
-- 分层存储：
--   base_image    静态底图：用户绘制的原始画面（不含任何动态内容）
--   overlay_dsl   动态图层：行式 DSL，描述动态元素的位置/类型/样式/数据绑定
--   bitmap_*      位图定义方案（转换模板）：目标单片机如何解读位图字节
-- =====================================================================
CREATE TABLE IF NOT EXISTS t_scene
(
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(64)  NOT NULL,               -- 场景名，如"书房天气面板"
    width       INT          NOT NULL DEFAULT 0,     -- 画布像素宽
    height      INT          NOT NULL DEFAULT 0,     -- 画布像素高
    base_image  BYTEA,                               -- 静态底图（PNG 或位图）
    base_hash   VARCHAR(64),                         -- 底图内容哈希
    overlay_dsl TEXT,                                -- 动态元素描述，见 docs/dynamic-content-design.md

    -- 位图定义方案：服务端内部规范色彩为 黑/白/红/黄/蓝/绿（最多 6 色），
    -- 下发前按下列配置把每个像素转换为目标单片机的颜色编码
    bitmap_bits       SMALLINT,                      -- 每像素位宽 1/2/3/4，NULL 按最大编码自动推导
    bitmap_bit_order  VARCHAR(4),                    -- 字节内位序：msb/lsb
    bitmap_black      CHAR(1),                       -- 黑色编码（十六进制单字符 0-F），NULL=不支持
    bitmap_white      CHAR(1),                       -- 白色编码
    bitmap_red        CHAR(1),                       -- 红色编码
    bitmap_yellow     CHAR(1),                       -- 黄色编码
    bitmap_blue       CHAR(1),                       -- 蓝色编码
    bitmap_green      CHAR(1),                       -- 绿色编码

    status      SMALLINT     NOT NULL DEFAULT 1,     -- 1:启用 0:已删除(软删)
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  t_scene             IS '画面场景表：保存的原始画面 + 动态图层描述';
COMMENT ON COLUMN t_scene.base_image  IS '静态底图二进制，只含原始内容，不含动态数据';
COMMENT ON COLUMN t_scene.overlay_dsl IS '动态元素 DSL 文本（text/chart/datetime/qr...），渲染时叠加在底图上';
COMMENT ON COLUMN t_scene.bitmap_bits      IS '位图定义方案：每像素位宽 1/2/3/4，NULL 自动推导';
COMMENT ON COLUMN t_scene.bitmap_bit_order IS '位图定义方案：字节内位序 msb/lsb';
COMMENT ON COLUMN t_scene.bitmap_black     IS '位图定义方案：黑色编码（十六进制 0-F），NULL=不支持该色';
COMMENT ON COLUMN t_scene.bitmap_white     IS '位图定义方案：白色编码（十六进制 0-F），NULL=不支持该色';
COMMENT ON COLUMN t_scene.bitmap_red       IS '位图定义方案：红色编码（十六进制 0-F），NULL=不支持该色';
COMMENT ON COLUMN t_scene.bitmap_yellow    IS '位图定义方案：黄色编码（十六进制 0-F），NULL=不支持该色';
COMMENT ON COLUMN t_scene.bitmap_blue      IS '位图定义方案：蓝色编码（十六进制 0-F），NULL=不支持该色';
COMMENT ON COLUMN t_scene.bitmap_green     IS '位图定义方案：绿色编码（十六进制 0-F），NULL=不支持该色';

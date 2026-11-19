-- =====================================================================
-- 墨水屏设备表：多台设备
-- 上位机（设备端）通过 device_sn + device_key 访问服务端，拉取当前应显示的内容
-- 设备不直接持有画面，而是引用一个场景；服务端渲染出的最终位图哈希用于增量比对
-- =====================================================================
CREATE TABLE IF NOT EXISTS t_device
(
    id                BIGSERIAL PRIMARY KEY,
    device_sn         VARCHAR(64)  NOT NULL,          -- 设备序列号，全局唯一
    device_key        VARCHAR(128) NOT NULL,          -- 设备访问凭证（校验上位机请求合法性）
    name              VARCHAR(64),                    -- 设备别名，如"书房日历屏"
    model             VARCHAR(64),                    -- 硬件型号，如 ESP32-DEPG0213
    width             INT          NOT NULL DEFAULT 0,-- 屏幕像素宽
    height            INT          NOT NULL DEFAULT 0,-- 屏幕像素高
    rotate            SMALLINT     NOT NULL DEFAULT 0,-- 旋转角度 0/90/180/270
    current_scene_id  BIGINT,                         -- 当前绑定的场景，见 t_scene
    current_hash      VARCHAR(64),                    -- 当前最终渲染结果哈希；设备轮询比对，相同则无需刷新
    status            SMALLINT     NOT NULL DEFAULT 0,-- 0:离线 1:在线 2:未激活
    battery           SMALLINT,                       -- 最近上报电量（百分比）
    last_online_at    TIMESTAMP,                      -- 最后一次上报/拉取时间
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_device_sn ON t_device (device_sn);

ALTER TABLE t_device
    ADD CONSTRAINT fk_device_scene FOREIGN KEY (current_scene_id) REFERENCES t_scene (id);

COMMENT ON TABLE  t_device               IS '墨水屏设备表';
COMMENT ON COLUMN t_device.device_sn     IS '设备序列号，上位机固件烧录，全局唯一';
COMMENT ON COLUMN t_device.device_key    IS '设备访问凭证，上位机拉取画面时校验';
COMMENT ON COLUMN t_device.current_scene_id IS '当前绑定的场景；场景或其数据变化后由服务端重新渲染';
COMMENT ON COLUMN t_device.current_hash  IS '最终渲染位图哈希；设备轮询时带上旧哈希，一致则服务端告知无需刷新';
COMMENT ON COLUMN t_device.status        IS '设备状态：0-离线，1-在线，2-未激活';

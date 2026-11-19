-- =====================================================================
-- gazette-press 初始化脚本
-- 用法：psql -U <user> -d <database> -f sql/init.sql
-- 说明：使用 \ir 按相对路径引入各建表脚本
-- 执行顺序：用户 -> 画面场景 -> 设备（外键依赖：scene->user, device->user/scene）
-- =====================================================================
\set ON_ERROR_STOP on

\ir 01_user.sql
\ir 02_scene.sql
\ir 03_device.sql

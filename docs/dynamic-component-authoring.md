# 画布组件编写方案（组件库）

> 状态：v2（画布页组件库版）
> 前端：`gazzette-desk/src/components/library/`

---

## 1. 定位与架构

应用只保留**画布页**（CanvasView）一个主页面：左侧是**组件库侧边栏（ComponentLibrary）**，
右侧是像素画布。组件的使用方式：

```
组件库卡片 ──点击──► 预览弹窗（ComponentPreviewModal，离屏渲染真实图案）
    │
    └──拖拽──► 像素画布（PixelCanvas drop）──► stampComponent() 烙印进画布格子
                                                      │
                                              蓝牙推送 / 导出位图
```

数据核心在 `library.ts`：

| 导出 | 职责 |
|---|---|
| `LibComponent` | 组件定义（id/name/desc/icon/w/h/kind/template/attrs） |
| `BUILTIN_COMPONENTS` | 内置组件：文本、时间日期、图标、二维码、直线、矩形、折线图 |
| `listComponents / getLibComponent` | 内置 + localStorage 自定义组件合并读取 |
| `addCustomComponent / removeCustomComponent` | 「创建组件」表单 → 自定义组件持久化 |
| `renderToCells(comp)` | 离屏渲染 → `'#'/.'` 格子图案（'#' = 墨水色，'.' = 透明） |
| `stampComponent(comp, ox, oy)` | 把图案烙进画布格子（透明像素不覆盖已有内容，支持撤销） |

## 2. LibComponent 接口

```ts
export interface LibComponent {
  id: string          // 内置固定 id；自定义组件为 custom:<时间戳>
  name: string        // 库中显示名
  desc: string        // 预览弹窗中的说明
  icon: string        // PixelIcon 名称（库卡片图标）
  w: number; h: number  // 组件尺寸（画布像素）
  kind: 'text' | 'datetime' | 'icon' | 'qr' | 'line' | 'rect' | 'chart'
  template?: string   // 文本模板，支持 {变量} 占位
  attrs: Record<string, string>  // font / format / fill / thickness 等
  custom?: boolean
}
```

## 3. 新增一个组件的步骤

以 `progress` 进度条为例：

**① `library.ts`：`LibKind` 增加 `'progress'`，`BUILTIN_COMPONENTS` 注册：**

```ts
{
  id: 'progress',
  name: '进度条',
  desc: '绑定 {battery} 的进度条（画布上为样例值）',
  icon: 'screen',
  w: 40,
  h: 8,
  kind: 'progress',
  attrs: { value: '{battery}', max: '100' },
},
```

**② `drawComponent()` 增加 `case 'progress'` 分支**：
按 `attrs.value/max` 画外框与实心条。渲染目标是 1:1 像素（1 canvas px = 1 画布格子），
只画黑色，`renderToCells` 会按亮度阈值转成 `'#'/.'` 图案。

**③ 预览弹窗（可选）**：`ComponentPreviewModal` 的 meta 区补一行展示新 attr。

内置组件预览时的变量取值来自 `SAMPLE_DATA`（如 `{indoor_temp}` → `26.5`）；
真实数据绑定由云端渲染负责（见 `docs/dynamic-content-design.md`），画布上的图案是示意。

## 4. 自定义组件（创建组件）

侧边栏「创建组件」按钮 → 表单（名称 / 宽 / 高 / 字号 / 文本模板）→
`addCustomComponent()` 生成 `kind: 'text'` 的自定义组件，存入
localStorage（`paper-web/library-custom`）。自定义组件与内置组件拥有完全相同的能力：
点击预览、拖拽放置、可删除（`removeCustomComponent`）。

## 5. 约定

| 规则 | 说明 |
|---|---|
| 渲染坐标系 | 1 canvas 像素 = 1 画布格子，组件以左上角 (0,0) 绘制 |
| 文字渲染 | 点阵字库（后端 `GET /api/font`，文件 `module/device/resources/font/pixel-font.json`）：CJK 16×16、ASCII 10×16，行高 16 点；字库缺失字符回退浏览器超采样栅格 |
| 字号缩放 | 文字按 `框高/16` 缩放点阵：组件框放大，文字随之放大；不需要单独的字号属性 |
| 颜色 | `drawComponent` 只画黑色；`renderToCells` 按亮度 < 150 判定为墨水像素 |
| 透明合成 | 烙印时仅 `'#'` 像素写入画布，`'.'` 不覆盖已有内容 |
| 撤销 | `stampComponent` 内部先 `pushUndo()`，Ctrl+Z 可整体撤销一次放置 |
| 位置钳制 | 放置坐标自动钳制，组件完整落在画布内 |
| 占位说明 | `qr` / `chart` / `icon` 在画布上是示意图案，云端渲染才是最终效果 |

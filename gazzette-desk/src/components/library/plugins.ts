/**
 * 组件插件入口 —— 开发者在这里以代码方式注册自定义组件。
 *
 * 用法：调用 registerComponent 传入一个 LibComponent 定义，
 * 组件会出现在左侧组件库的「插件组件」分组，与内置组件拥有完全相同的能力
 * （点击预览 / 拖拽放置 / 放置后随时拖动缩放、编辑文本，图层实时叠加渲染）。
 *
 * kind 支持的渲染类型见 library.ts 的 LibKind：
 *   text     文本模板（支持 {变量} 占位，放置后可编辑）
 *   datetime 时间（attrs.format，如 %H:%M）
 *   icon     天气图标占位
 *   qr       二维码示意图案（template = 链接）
 *   line     直线（attrs.thickness）
 *   rect     矩形（attrs.fill: '0' 描边 / '1' 实心）
 *   chart    折线图样例
 */
import { registerComponent } from './library'

// 示例：一个自定义文本组件（可按需修改或添加更多）
registerComponent({
  id: 'plugin:welcome',
  name: '欢迎语',
  desc: '插件示例：欢迎文本',
  icon: 'spark',
  w: 80,
  h: 16,
  kind: 'text',
  template: '你好，墨水屏！',
  attrs: {},
})

// 示例：实心色块
registerComponent({
  id: 'plugin:block',
  name: '色块',
  desc: '插件示例：实心矩形',
  icon: 'rect',
  w: 16,
  h: 8,
  kind: 'rect',
  attrs: { fill: '1' },
})

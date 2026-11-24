/**
 * 组件插件入口 —— 开发者在这里以代码方式注册自定义组件。
 *
 * 用法：调用 registerComponent 传入一个 LibComponent 定义，
 * 组件会出现在左侧组件库的「插件组件」分组，按 group 字段折叠成子目录
 * （点击预览 / 拖拽放置 / 放置后随时拖动缩放、编辑文本，图层实时叠加渲染）。
 *
 * kind 支持的渲染类型见 library.ts 的 LibKind：
 *   text     文本模板（支持 {变量} 占位，展示时用虚拟示例内容，放置后可编辑）
 *   chart    折线图（标题/轴标签/横轴刻度可配置，画布为虚拟样例，云端实时拉取数据栅格化）
 *   pie      饼图（占比标注、空缺数据显示"无数据"）
 *
 * AI 用量组件（融合 GLM + DeepSeek）：
 *   - needsApiKey: true → 放置后需在「数据源设置」配置对应数据源（用什么配什么）
 *   - attrs.provider    → 依赖的数据源（glm / deepseek / ai=两家都要）
 *   - 用量数据不落库，服务端每次渲染实时向平台拉取（60s 内存缓存防频控）
 */
import { registerComponent } from './library'

/* ---------------------------------------------------- AI 用量（融合） */

registerComponent({
  id: 'plugin:ai-overview',
  group: 'AI 用量',
  name: '用量总览',
  desc: 'AI 累计 token + 账户余额，变量可编辑（{ai.total_tokens} / {ai.balance.total}）',
  icon: 'pen',
  w: 150,
  h: 16,
  kind: 'text',
  template: 'AI {ai.total_tokens} tok · 余额 {ai.balance.total} 元',
  needsApiKey: true,
  attrs: { provider: 'ai' },
})

registerComponent({
  id: 'plugin:ai-pie',
  group: 'AI 用量',
  name: '用量构成饼图',
  desc: 'GLM 与 DeepSeek 用量占比（系列 {ai.usage.pie_seq}，空缺显示"无数据"）',
  icon: 'screen',
  w: 48,
  h: 32,
  kind: 'pie',
  needsApiKey: true,
  attrs: { provider: 'ai', series: 'ai.usage.pie_seq', title: 'AI 用量构成' },
})

registerComponent({
  id: 'plugin:ai-line-month',
  group: 'AI 用量',
  name: 'GLM 月度折线图',
  desc: '近 12 个月用量，未使用月份填 0（{glm.usage.month_seq}，横轴为月份标签）',
  icon: 'image',
  w: 90,
  h: 44,
  kind: 'chart',
  needsApiKey: true,
  attrs: {
    provider: 'glm',
    series: 'glm.usage.month_seq',
    'x-ticks': 'glm.usage.month_labels',
    title: 'GLM 月度用量',
    'x-label': '月份',
    'y-label': 'tokens',
    points: '12',
  },
})

registerComponent({
  id: 'plugin:ai-ds-line-month',
  group: 'AI 用量',
  name: 'DS 月度折线图',
  desc: '近 12 个月用量（需平台 Cookie/账号密码方式，实时查询平台用量）',
  icon: 'image',
  w: 90,
  h: 44,
  kind: 'chart',
  needsApiKey: true,
  attrs: {
    provider: 'deepseek',
    series: 'deepseek.usage.month_seq',
    'x-ticks': 'deepseek.usage.month_labels',
    title: 'DeepSeek 月度用量',
    'x-label': '月份',
    'y-label': 'tokens',
    points: '12',
  },
})

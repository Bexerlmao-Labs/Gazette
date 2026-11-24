/**
 * 变量注册表 —— 设备动态文本的数据绑定（见 docs/dynamic-content-design.md §4）。
 *
 * 组件模板里的 {变量} 占位符在编辑时写入；画布/预览（设计视图）展示时：
 *   - 已注册变量 → 替换为注册表中的虚拟示例值（不显示变量名本身）
 *   - 未注册变量 → 原样保留，便于发现拼写错误
 * 设备端最终位图由服务端渲染：数据源实时组装数据集，变量缺失则跳过该元素。
 *
 * 序列说明：
 *   - month_seq 固定近 12 个月档位，未使用月份填 0（空缺月份处理），month_labels 标注月份；
 *   - 折线图 chart 的 x-ticks 属性指向 *_labels 变量绘制横轴刻度；
 *   - 用量数据不落库：服务端渲染时实时向平台拉取（仅 60s 内存缓存防频控）。
 */
export interface VariableDef {
  /** 点分命名空间变量名，如 ai.total_tokens */
  name: string
  desc: string
  /** 设计视图展示用的虚拟示例值 */
  sample: string
}

export interface VariableNamespace {
  /** 命名空间前缀 */
  namespace: string
  label: string
  desc: string
  vars: VariableDef[]
}

export const VARIABLE_NAMESPACES: VariableNamespace[] = [
  {
    namespace: 'ai',
    label: 'AI 用量（融合）',
    desc: 'GLM + DeepSeek 统一汇总，推荐使用',
    vars: [
      { name: 'ai.total_tokens', desc: 'AI 累计 token（GLM 账单 + DeepSeek）', sample: '128640' },
      { name: 'ai.balance.total', desc: 'AI 账户余额（DeepSeek，元）', sample: '42.00' },
      { name: 'ai.usage.pie_seq', desc: '用量构成 [GLM, DeepSeek]（饼图）', sample: '[98000, 30640]' },
    ],
  },
  {
    namespace: 'glm',
    label: 'GLM（智谱）',
    desc: '账单用量接口，需配置 API Key',
    vars: [
      { name: 'glm.usage.total_tokens', desc: '近 7 天累计 token 用量', sample: '128640' },
      { name: 'glm.usage.requests', desc: '近 7 天调用次数', sample: '96' },
      { name: 'glm.usage.seq', desc: '近 7 天每日用量序列', sample: '[每日 token 数]' },
      { name: 'glm.usage.seq_labels', desc: '近 7 天日期标签（x-ticks）', sample: '[01-01 … 01-07]' },
      { name: 'glm.usage.month_seq', desc: '近 12 个月用量（空缺月=0）', sample: '[每月 token 数]' },
      { name: 'glm.usage.month_labels', desc: '近 12 个月月份标签（x-ticks）', sample: '[2025-01 …]' },
    ],
  },
  {
    namespace: 'deepseek',
    label: 'DeepSeek',
    desc: '余额走官方接口；用量需平台 Cookie/账号密码登录态',
    vars: [
      { name: 'deepseek.balance.total', desc: '账户总余额（元）', sample: '0.27' },
      { name: 'deepseek.balance.granted', desc: '赠送余额（元）', sample: '0.00' },
      { name: 'deepseek.balance.topped_up', desc: '充值余额（元）', sample: '0.27' },
      { name: 'deepseek.balance.pie_seq', desc: '余额构成 [赠送, 充值]（饼图）', sample: '[0, 0.27]' },
      { name: 'deepseek.usage.total_tokens', desc: '累计 token（平台登录态实时查询）', sample: '30640' },
      { name: 'deepseek.usage.month_seq', desc: '近 12 个月用量（空缺月=0）', sample: '[每月 token 数]' },
      { name: 'deepseek.usage.month_labels', desc: '月份标签（x-ticks）', sample: '[2025-01 …]' },
    ],
  },
]

export function allVariables(): VariableDef[] {
  return VARIABLE_NAMESPACES.flatMap((ns) => ns.vars)
}

/** 解析 {变量} 模板：设计视图用虚拟示例值展示，未注册变量原样保留 */
export function resolveTemplate(template: string): string {
  return template.replace(/\{([^{}]+)\}/g, (match, name: string) => {
    const def = allVariables().find((v) => v.name === name.trim())
    return def ? def.sample : match
  })
}

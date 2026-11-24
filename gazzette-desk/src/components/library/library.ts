/**
 * 画布组件库 —— 左侧组件库的数据与渲染核心。
 *
 * 组件的使用方式：
 *  - 点击 → 预览（ComponentPreviewModal 用 renderToCells 渲染）
 *  - 拖拽/放置 → 成为画布上的「放置组件」图层，随时可拖动、拖角改大小、编辑文本，
 *    内容实时叠加渲染到画布，导出/推送时合并进像素数据；没有落定步骤。
 *
 * 自定义组件由开发者以插件方式注册（plugins.ts 里调 registerComponent），不提供用户侧创建入口。
 */

import { useDisplayStore } from '@/stores/display'
import { ensureFontLoaded, textToBuffer } from './font'

export type LibKind = 'text' | 'datetime' | 'icon' | 'qr' | 'line' | 'rect' | 'chart'

export interface LibComponent {
  /** 内置组件为固定 id；插件组件建议 `plugin:<名>` */
  id: string
  name: string
  desc: string
  /** PixelIcon 名称 */
  icon: string
  /** 组件初始尺寸（画布像素），放置后可拖拽调整 */
  w: number
  h: number
  kind: LibKind
  /** 文本模板，支持 {变量} 占位 */
  template?: string
  attrs: Record<string, string>
}

/** 放置在画布上的组件实例（图层，可随时移动/缩放/编辑，内容实时叠加到画布） */
export interface PlacedComponent {
  uid: string
  /** 组件定义快照（编辑文本直接改这里的 template/attrs） */
  comp: LibComponent
  x: number
  y: number
  w: number
  h: number
}

/* ------------------------------------------------------------- 内置组件 */
/** strftime 子集：%H %M %S %d %m %Y */
function strftime(fmt: string, d: Date): string {
  const p = (n: number) => String(n).padStart(2, '0')
  return fmt
    .replace(/%H/g, p(d.getHours()))
    .replace(/%M/g, p(d.getMinutes()))
    .replace(/%S/g, p(d.getSeconds()))
    .replace(/%d/g, p(d.getDate()))
    .replace(/%m/g, p(d.getMonth() + 1))
    .replace(/%Y/g, String(d.getFullYear()))
}

/* ------------------------------------------------------------- 内置组件 */

export const BUILTIN_COMPONENTS: LibComponent[] = [
  {
    id: 'text',
    name: '文本',
    desc: '文本，放置后可编辑内容',
    icon: 'pen',
    w: 110,
    h: 16,
    kind: 'text',
    template: '你好',
    attrs: {},
  },
  {
    id: 'datetime',
    name: '时间日期',
    desc: '当前时间，%H:%M 等格式；放置后可编辑格式',
    icon: 'spark',
    w: 56,
    h: 16,
    kind: 'datetime',
    attrs: { format: '%H:%M' },
  },
  {
    id: 'icon',
    name: '图标',
    desc: '天气图标占位；云端渲染时按 {weather.icon} 从图标集取图',
    icon: 'image',
    w: 12,
    h: 12,
    kind: 'icon',
    attrs: {},
  },
  {
    id: 'qr',
    name: '二维码',
    desc: '二维码（画布上为示意图案，云端渲染为真实码点）；放置后可编辑链接',
    icon: 'code',
    w: 20,
    h: 20,
    kind: 'qr',
    template: 'https://bexerlmao.cc',
    attrs: {},
  },
  {
    id: 'line',
    name: '直线',
    desc: '装饰 / 分隔线',
    icon: 'rect',
    w: 40,
    h: 2,
    kind: 'line',
    attrs: { thickness: '1' },
  },
  {
    id: 'rect',
    name: '矩形',
    desc: '矩形 / 色块，可描边或实心',
    icon: 'rect',
    w: 24,
    h: 12,
    kind: 'rect',
    attrs: { fill: '0' },
  },
  {
    id: 'chart',
    name: '折线图',
    desc: '序列数据图（画布上为样例曲线，云端按数据集栅格化）',
    icon: 'screen',
    w: 48,
    h: 24,
    kind: 'chart',
    attrs: {},
  },
]

/* ------------------------------------------------------------- 插件注册 */

const pluginComponents: LibComponent[] = []

/**
 * 插件式注册自定义组件（开发者在 plugins.ts 中调用，不出现在用户界面）。
 * id 冲突时忽略后注册的。
 */
export function registerComponent(def: LibComponent) {
  const exists = [...BUILTIN_COMPONENTS, ...pluginComponents].some((c) => c.id === def.id)
  if (exists) {
    console.warn(`[组件库] 组件 id 已存在，忽略注册: ${def.id}`)
    return
  }
  pluginComponents.push({ ...def, attrs: { ...def.attrs } })
}

export function listComponents(): LibComponent[] {
  return [...BUILTIN_COMPONENTS, ...pluginComponents]
}

export function getLibComponent(id: string): LibComponent | undefined {
  return listComponents().find((c) => c.id === id)
}

/* ------------------------------------------------------------- 渲染 */

/** 在离屏 canvas 上按组件种类绘制（1 canvas 像素 = 1 画布格子） */
function drawComponent(ctx: CanvasRenderingContext2D, comp: LibComponent) {
  const w = comp.w
  const h = comp.h
  ctx.fillStyle = '#000000'
  ctx.strokeStyle = '#000000'
  ctx.lineWidth = 1

  /**
   * 点阵文字：按「完全容纳」进组件框的整数倍放大（1x/2x/3x…），
   * 小于 1x 时按目标像素覆盖率面积采样缩小 —— 任意尺寸下笔画都保持锐利。
   */
  const drawText = (text: string) => {
    const buf = textToBuffer(text)
    if (!buf.width || !buf.height) return
    const raw = Math.min(w / buf.width, h / buf.height)
    const s = raw >= 1 ? Math.floor(raw) : raw
    if (s <= 0) return
    const ox = Math.round((w - buf.width * s) / 2)
    const oy = Math.round((h - buf.height * s) / 2)

    for (let y = 0; y < h; y++) {
      const sy0 = (y - oy) / s
      const sy1 = sy0 + 1 / s
      const row0 = Math.max(0, Math.floor(sy0))
      const row1 = Math.min(buf.height, Math.ceil(sy1))
      if (row0 >= buf.height) break
      for (let x = 0; x < w; x++) {
        const sx0 = (x - ox) / s
        const sx1 = sx0 + 1 / s
        const col0 = Math.max(0, Math.floor(sx0))
        const col1 = Math.min(buf.width, Math.ceil(sx1))
        if (col0 >= buf.width) break

        // 目标像素覆盖的源格区域面积加权采样
        let cov = 0
        let area = 0
        for (let sy = row0; sy < row1; sy++) {
          const ovh = Math.min(sy1, sy + 1) - Math.max(sy0, sy)
          if (ovh <= 0) continue
          const row = buf.rows[sy]
          for (let sx = col0; sx < col1; sx++) {
            const ovw = Math.min(sx1, sx + 1) - Math.max(sx0, sx)
            if (ovw <= 0) continue
            area += ovw * ovh
            if (row[sx] === '#') cov += ovw * ovh
          }
        }
        if (area > 0 && cov / area > 0.5) ctx.fillRect(x, y, 1, 1)
      }
    }
  }

  switch (comp.kind) {
    case 'text':
      drawText(comp.template ?? comp.name)
      break
    case 'datetime':
      drawText(strftime(comp.attrs.format || '%H:%M', new Date()))
      break
    case 'line':
      ctx.fillRect(0, 0, w, Math.max(1, Math.min(h, Number(comp.attrs.thickness) || 1)))
      break
    case 'rect': {
      if (comp.attrs.fill === '1') {
        ctx.fillRect(0, 0, w, h)
      } else {
        ctx.fillRect(0, 0, w, 1)
        ctx.fillRect(0, h - 1, w, 1)
        ctx.fillRect(0, 0, 1, h)
        ctx.fillRect(w - 1, 0, 1, h)
      }
      break
    }
    case 'icon': {
      // 天气图标占位：太阳（圆 + 光芒）
      const cx = w / 2
      const cy = h / 2
      const r = Math.min(w, h) / 4
      ctx.beginPath()
      ctx.arc(cx, cy, r, 0, Math.PI * 2)
      ctx.stroke()
      for (let i = 0; i < 8; i++) {
        const a = (Math.PI / 4) * i
        ctx.beginPath()
        ctx.moveTo(cx + Math.cos(a) * (r + 1), cy + Math.sin(a) * (r + 1))
        ctx.lineTo(cx + Math.cos(a) * (r + 2.5), cy + Math.sin(a) * (r + 2.5))
        ctx.stroke()
      }
      break
    }
    case 'chart': {
      // 样例曲线（云端按数据集真实渲染）
      const series = [3, 5, 4, 7, 6, 9, 8, 11, 10, 13]
      const pad = 1
      const max = Math.max(...series)
      const min = Math.min(...series)
      const span = Math.max(1, max - min)
      ctx.beginPath()
      series.forEach((v, i) => {
        const x = pad + (i * (w - pad * 2)) / (series.length - 1)
        const y = h - pad - ((v - min) / span) * (h - pad * 2)
        if (i === 0) ctx.moveTo(x, y)
        else ctx.lineTo(x, y)
      })
      ctx.stroke()
      break
    }
    case 'qr': {
      // 示意二维码：三个定位角 + 由模板决定的伪随机模块
      const m = Math.min(w, h)
      const modules = Math.max(8, Math.floor(m / 2))
      const cell = m / modules
      const inFinder = (mx: number, my: number) =>
        (mx < 4 && my < 4) || (mx >= modules - 4 && my < 4) || (mx < 4 && my >= modules - 4)
      const drawFinder = (gx: number, gy: number) => {
        ctx.fillRect(gx * cell, gy * cell, cell * 3, cell * 3)
        ctx.save()
        ctx.fillStyle = '#ffffff'
        ctx.fillRect((gx + 1) * cell, (gy + 1) * cell, cell, cell)
        ctx.restore()
        ctx.fillStyle = '#000000'
      }
      drawFinder(0, 0)
      drawFinder(modules - 3, 0)
      drawFinder(0, modules - 3)

      // 简单种子伪随机
      let seed = 0
      for (const ch of comp.template ?? comp.id) seed = (seed * 31 + ch.charCodeAt(0)) | 0
      const rand = () => {
        seed = (seed * 1103515245 + 12345) | 0
        return ((seed >>> 16) & 0xffff) / 0xffff
      }
      for (let my = 0; my < modules; my++) {
        for (let mx = 0; mx < modules; mx++) {
          if (inFinder(mx, my) || rand() < 0.5) continue
          ctx.fillRect(mx * cell, my * cell, Math.ceil(cell), Math.ceil(cell))
        }
      }
      break
    }
  }
}

/**
 * 渲染组件为格子图案：返回长度 w*h 的字符串，'#' = 墨水色像素，'.' = 透明。
 * 透明像素烙印到画布时不会覆盖已有内容。
 */
export function renderToCells(comp: LibComponent): string {
  const w = Math.max(1, Math.round(comp.w))
  const h = Math.max(1, Math.round(comp.h))
  const canvas = document.createElement('canvas')
  canvas.width = w
  canvas.height = h
  const ctx = canvas.getContext('2d', { willReadFrequently: true })
  if (!ctx) return '.'.repeat(w * h)

  ctx.fillStyle = '#ffffff'
  ctx.fillRect(0, 0, w, h)
  drawComponent(ctx, { ...comp, w, h })

  const data = ctx.getImageData(0, 0, w, h).data
  let out = ''
  for (let i = 0; i < w * h; i++) {
    const r = data[i * 4]
    const g = data[i * 4 + 1]
    const b = data[i * 4 + 2]
    const lum = 0.299 * r + 0.587 * g + 0.114 * b
    out += lum < 150 ? '#' : '.'
  }
  return out
}

/* ------------------------------------------------------------- 放置组件 */

let placedSeq = 0

/** 文本类内容在 1x 点阵下的实际尺寸（画布像素） */
export function textContentSize(comp: LibComponent): { w: number; h: number } | null {
  const text =
    comp.kind === 'datetime' ? strftime(comp.attrs.format || '%H:%M', new Date()) : comp.template ?? ''
  const buf = textToBuffer(text)
  if (!buf.width || !buf.height) return null
  return { w: buf.width, h: buf.height }
}

/** 把组件放到画布上（成为可随时拖动/缩放/编辑的图层）；
 *  文本/时间按内容自适应：取能放进画布的最大整数倍（≤3x），保证点阵清晰可读 */
export async function placeComponent(comp: LibComponent, ox: number, oy: number) {
  await ensureFontLoaded()
  const store = useDisplayStore()
  let w = comp.w
  let h = comp.h
  if (comp.kind === 'text' || comp.kind === 'datetime') {
    const size = textContentSize(comp)
    if (size) {
      const s = Math.max(
        1,
        Math.min(3, Math.floor(Math.min(store.width / size.w, store.height / size.h))),
      )
      w = Math.min(size.w * s + 2, store.width)
      h = Math.min(size.h * s + 2, store.height)
    }
  }
  w = Math.min(Math.max(4, Math.round(w)), store.width)
  h = Math.min(Math.max(4, Math.round(h)), store.height)
  const x = Math.max(0, Math.min(Math.round(ox), store.width - w))
  const y = Math.max(0, Math.min(Math.round(oy), store.height - h))
  store.addPlaced({
    uid: `pc-${Date.now().toString(36)}-${placedSeq++}`,
    comp: { ...comp, attrs: { ...comp.attrs } },
    x,
    y,
    w,
    h,
  })
}

/** 放置组件的当前点阵图案（透明像素不覆盖下层内容） */
export function placedPattern(pc: PlacedComponent): string {
  return renderToCells({ ...pc.comp, w: pc.w, h: pc.h })
}

/** 文本类放置组件：内容在当前框内连 1x 都放不下时，把框扩大到刚好容纳（避免降采样发糊） */
export function fitPlacedText(pc: PlacedComponent) {
  const store = useDisplayStore()
  if (pc.comp.kind !== 'text' && pc.comp.kind !== 'datetime') return
  const size = textContentSize(pc.comp)
  if (!size) return
  const s = Math.floor(Math.min(pc.w / size.w, pc.h / size.h))
  if (s < 1) {
    pc.w = Math.min(Math.max(4, size.w + 2), store.width - pc.x)
    pc.h = Math.min(Math.max(4, size.h + 2), store.height - pc.y)
  }
}

/** 把放置组件图层合并到底色网格上：返回叠加后的格子颜色数组 */
export function flattenPlaced(baseCells: string[], w: number, h: number): string[] {
  const out = baseCells.slice()
  const store = useDisplayStore()
  const ink = store.darkestColor.hex
  for (const pc of store.placedComponents) {
    const pattern = placedPattern(pc)
    for (let y = 0; y < pc.h; y++) {
      for (let x = 0; x < pc.w; x++) {
        if (pattern[y * pc.w + x] !== '#') continue
        const gx = pc.x + x
        const gy = pc.y + y
        if (gx < 0 || gy < 0 || gx >= w || gy >= h) continue
        out[gy * w + gx] = ink
      }
    }
  }
  return out
}

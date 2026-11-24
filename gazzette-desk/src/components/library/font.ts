/**
 * 点阵字库：从后端 /api/font 加载（module/device resources/font/pixel-font.json），
 * 前端文字组件用点阵渲染，不再依赖浏览器字体栅格化。
 *
 * 字库结构：{ size: 16, glyphs: { "室": ["....", ...16 行], "A": [...10 宽] } }
 * - CJK 全角 16×16、ASCII 半角 10×16，行高统一 16 点；
 * - 字库未加载或字符缺失时，回退为浏览器超采样栅格（内存缓存）；
 * - 渲染时按 组件框高度/16 缩放，框放大文字随之放大。
 */

export interface FontData {
  size: number
  glyphs: Record<string, string[]>
}

export const GLYPH_HEIGHT = 16

let fontData: FontData | null = null
let fontPromise: Promise<FontData | null> | null = null

/** 应用启动时预热；失败时静默回退到浏览器栅格 */
export function ensureFontLoaded(): Promise<FontData | null> {
  if (fontData) return Promise.resolve(fontData)
  if (!fontPromise) {
    fontPromise = fetch('/api/font')
      .then((r) => (r.ok ? r.json() : Promise.reject(new Error(`HTTP ${r.status}`))))
      .then((d: FontData) => {
        if (d && d.glyphs && Object.keys(d.glyphs).length > 0) {
          fontData = d
        }
        return fontData
      })
      .catch(() => null)
  }
  return fontPromise
}

/** 字库缺失字符的浏览器栅格兜底（128px 超采样 → 16×16） */
const fallbackCache = new Map<string, string[]>()

function rasterFallback(ch: string): string[] {
  const cached = fallbackCache.get(ch)
  if (cached) return cached
  const size = 128
  const canvas = document.createElement('canvas')
  canvas.width = size
  canvas.height = size
  const ctx = canvas.getContext('2d', { willReadFrequently: true })
  if (!ctx) return Array.from({ length: GLYPH_HEIGHT }, () => '.'.repeat(16))
  ctx.fillStyle = '#fff'
  ctx.fillRect(0, 0, size, size)
  ctx.fillStyle = '#000'
  ctx.font = `bold ${Math.floor(size * 0.82)}px "Microsoft YaHei", sans-serif`
  ctx.textBaseline = 'middle'
  ctx.fillText(ch, size * 0.08, size / 2)

  const data = ctx.getImageData(0, 0, size, size).data
  const rows: string[] = []
  for (let gy = 0; gy < GLYPH_HEIGHT; gy++) {
    let row = ''
    for (let gx = 0; gx < GLYPH_HEIGHT; gx++) {
      let sum = 0
      const x0 = (gx * size) / GLYPH_HEIGHT
      const y0 = (gy * size) / GLYPH_HEIGHT
      for (let y = y0; y < y0 + size / GLYPH_HEIGHT; y++) {
        for (let x = x0; x < x0 + size / GLYPH_HEIGHT; x++) {
          const i = (y * size + x) * 4
          sum += 255 - data[i]
        }
      }
      const avg = sum / (size / GLYPH_HEIGHT) ** 2
      row += avg > 90 ? '#' : '.'
    }
    rows.push(row)
  }
  fallbackCache.set(ch, rows)
  return rows
}

export function glyphOf(ch: string): string[] | null {
  const fromFont = fontData?.glyphs[ch]
  if (fromFont) return fromFont
  let fb = fallbackCache.get(ch)
  if (!fb) {
    fb = rasterFallback(ch)
    fallbackCache.set(ch, fb)
  }
  return fb
}

export interface TextBuffer {
  /** 行宽（点数） */
  width: number
  /** 行高（恒为 GLYPH_HEIGHT） */
  height: number
  /** '#'/'.' 组成的行文本 */
  rows: string[]
}

/** 文本 → 16 点高的点阵行（字库 + 兜底栅格） */
export function textToBuffer(text: string): TextBuffer {
  const lines: string[] = Array.from({ length: GLYPH_HEIGHT }, () => '')
  let width = 0
  for (const ch of text) {
    const glyph = glyphOf(ch)
    if (!glyph || !glyph.length) continue // 不可渲染字符跳过
    const gw = glyph[0].length
    for (let y = 0; y < GLYPH_HEIGHT; y++) {
      lines[y] += (glyph[y] ?? '.'.repeat(gw)) + '.'
    }
    width += gw + 1 // 字形 + 1 点间距
  }
  return { width, height: GLYPH_HEIGHT, rows: lines }
}

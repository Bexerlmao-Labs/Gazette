/**
 * 点阵字库：前后端同源双份，格式一致（tools/fontgen_bdf.py 从
 * 「缝合像素字体 Fusion Pixel」BDF 原厂位图转换生成，SIL OFL 1.1）。
 * - 前端内置副本（src/assets/pixel-font.json）：启动即可用，绘制不依赖后端；
 * - 后端 /api/font（module/device resources/font）：服务端渲染动态内容的唯一真源；
 * - ensureFontLoaded 会尝试拉取后端字库，成功后覆盖前端副本并触发重绘，
 *   两份文件由同一脚本生成，内容一致；拉取失败时继续用内置副本。
 *
 * 字库结构：{ size: 16, glyphs: { "室": ["....", ...16 行], "A": [...7 宽] } }
 * - 字形为点阵字体原厂位图，基线对齐放在 16 行网格内，栅格干净，缩放不糊；
 * - 字符缺失时，回退为浏览器超采样栅格（内存缓存）。
 */

import { default as bundledFont } from '@/assets/pixel-font.json'

export interface FontData {
  size: number
  glyphs: Record<string, string[]>
}

export const GLYPH_HEIGHT = 16

let fontData: FontData | null = bundledFont
let backendSynced = false
let syncPromise: Promise<FontData | null> | null = null
const loadedCallbacks: Array<() => void> = []

/** 字库就绪/更新后触发的回调（用于触发画布/导出数据重算） */
export function onFontLoaded(cb: () => void) {
  loadedCallbacks.push(cb)
}

function replaceFont(next: FontData) {
  const changed = fontData !== next
  fontData = next
  if (changed) for (const cb of loadedCallbacks) cb()
}

/** 与后端字库同步：成功则以后端为准覆盖内置副本，失败则继续用内置副本 */
export function ensureFontLoaded(): Promise<FontData | null> {
  if (backendSynced) return Promise.resolve(fontData)
  if (!syncPromise) {
    syncPromise = fetch('/api/font')
      .then((r) => (r.ok ? r.json() : Promise.reject(new Error(`HTTP ${r.status}`))))
      .then((d: FontData) => {
        if (d && d.glyphs && Object.keys(d.glyphs).length > 0) {
          backendSynced = true
          replaceFont(d)
        }
        return fontData
      })
      .catch(() => {
        syncPromise = null // 后端不可用时下次调用重试
        return fontData
      })
  }
  return syncPromise
}

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
  /** 行高（字库行高，可能小于 GLYPH_HEIGHT） */
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
  if (!width) return { width: 0, height: 0, rows: [] }
  // 裁掉上下全空行，让文字充满组件框（避免字库行高空隙导致缩放偏小）
  let top = 0
  let bottom = lines.length - 1
  while (top < bottom && !lines[top].includes('#')) top++
  while (bottom > top && !lines[bottom].includes('#')) bottom--
  const rows = lines.slice(top, bottom + 1)
  return { width, height: rows.length, rows }
}

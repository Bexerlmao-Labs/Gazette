export interface ConvertOptions {
  brightness: number // -100 ~ 100
  contrast: number // -100 ~ 100
  dither: boolean
  invert: boolean
}

interface Rgb {
  hex: string
  r: number
  g: number
  b: number
}

function hexToRgb(hex: string): Rgb {
  const value = Number.parseInt(hex.slice(1), 16)
  return { hex, r: (value >> 16) & 255, g: (value >> 8) & 255, b: value & 255 }
}

/** 在墨水色列表中找到与 (r, g, b) 感知距离最近的颜色 */
function nearest(r: number, g: number, b: number, colors: Rgb[]): Rgb {
  let best = colors[0]!
  let bestDistance = Number.POSITIVE_INFINITY
  for (const c of colors) {
    const dr = r - c.r
    const dg = g - c.g
    const db = b - c.b
    const distance = dr * dr * 2 + dg * dg * 4 + db * db
    if (distance < bestDistance) {
      bestDistance = distance
      best = c
    }
  }
  return best
}

/**
 * 将降采样后的照片像素转换为墨水屏颜色。
 * 每个像素都被量化为 `palette` 中最接近的自定义颜色；
 * 透明像素按接近白色的墨水色处理；
 * `dither` 开启时使用 Floyd–Steinberg 误差扩散，让过渡更自然。
 */
export function convertPixels(
  data: Uint8ClampedArray,
  width: number,
  height: number,
  options: ConvertOptions,
  palette: readonly string[],
): string[] {
  const colors = palette.map(hexToRgb)
  const count = width * height
  const out: string[] = Array.from({ length: count }, () => colors[0]?.hex ?? '#ffffff')
  if (!colors.length) return out

  // 透明区域使用最接近纯白的墨水色
  const fallback = nearest(255, 255, 255, colors)

  const buf = new Float32Array(count * 3)
  const alpha = new Uint8Array(count)

  const brightnessOffset = options.brightness * 2.55
  const contrastFactor = 1 + options.contrast / 100
  const adjust = (v: number) => {
    const next = (v - 128) * contrastFactor + 128 + brightnessOffset
    return next < 0 ? 0 : next > 255 ? 255 : next
  }

  for (let i = 0; i < count; i++) {
    const o = i * 4
    let r = data[o] ?? 0
    let g = data[o + 1] ?? 0
    let b = data[o + 2] ?? 0
    alpha[i] = data[o + 3] ?? 255

    if (options.invert) {
      r = 255 - r
      g = 255 - g
      b = 255 - b
    }

    buf[i * 3] = adjust(r)
    buf[i * 3 + 1] = adjust(g)
    buf[i * 3 + 2] = adjust(b)
  }

  if (!options.dither) {
    for (let i = 0; i < count; i++) {
      if ((alpha[i] ?? 255) < 128) {
        out[i] = fallback.hex
        continue
      }
      out[i] = nearest(
        clampByte(buf[i * 3] ?? 0),
        clampByte(buf[i * 3 + 1] ?? 0),
        clampByte(buf[i * 3 + 2] ?? 0),
        colors,
      ).hex
    }
    return out
  }

  // Floyd–Steinberg 误差扩散（在 RGB 空间中对墨水色量化）
  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      const i = y * width + x
      if ((alpha[i] ?? 255) < 128) {
        out[i] = fallback.hex
        continue
      }
      const r = clampByte(buf[i * 3] ?? 0)
      const g = clampByte(buf[i * 3 + 1] ?? 0)
      const b = clampByte(buf[i * 3 + 2] ?? 0)
      const pick = nearest(r, g, b, colors)
      out[i] = pick.hex

      const er = r - pick.r
      const eg = g - pick.g
      const eb = b - pick.b

      const distribute = (dx: number, dy: number, factor: number) => {
        const nx = x + dx
        const ny = y + dy
        if (nx < 0 || nx >= width || ny >= height) return
        const ni = ny * width + nx
        if ((alpha[ni] ?? 255) < 128) return
        buf[ni * 3] = (buf[ni * 3] ?? 0) + er * factor
        buf[ni * 3 + 1] = (buf[ni * 3 + 1] ?? 0) + eg * factor
        buf[ni * 3 + 2] = (buf[ni * 3 + 2] ?? 0) + eb * factor
      }

      distribute(1, 0, 7 / 16)
      distribute(-1, 1, 3 / 16)
      distribute(0, 1, 5 / 16)
      distribute(1, 1, 1 / 16)
    }
  }

  return out
}

function clampByte(v: number) {
  return v < 0 ? 0 : v > 255 ? 255 : Math.round(v)
}

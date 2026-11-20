/** 后端统一响应包装 */
export interface ApiResult<T> {
  code: number
  message: string
  data: T
}

/** 位图定义方案（转换模板）：目标单片机如何解读位图字节，后端拆列存储 */
export interface BitmapEncoding {
  /** 每像素位宽 1/2/3/4，不传则按最大编码自动推导 */
  bits?: number
  /** 字节内位序 */
  bitOrder: 'msb' | 'lsb'
  /** 各颜色编码（十六进制单字符 0-F），空字符串 = 不支持该色；黑/白默认 1/0（GxEPD2 风格） */
  black: string
  white: string
  red: string
  yellow: string
  blue: string
  green: string
}

/** 画面场景：原始底图 + 动态 DSL */
export interface CloudScene {
  id: number
  name: string
  width: number
  height: number
  baseImage?: string // Base64 PNG
  baseHash?: string
  overlayDsl?: string
  bitmapEncoding?: BitmapEncoding
  updatedAt: string
}

/** 墨水屏设备 */
export interface CloudDevice {
  id: number
  deviceSn: string
  deviceKey: string
  name: string
  model?: string
  width: number
  height: number
  rotate: number
  currentSceneId: number | null
  currentHash: string | null
  /** 0:离线 1:在线 2:未激活 */
  status: number
  battery?: number
  lastOnlineAt?: string
  createdAt: string
}

/** 固件拉取接口响应 */
export interface DeviceDisplay {
  changed: boolean
  hash?: string
  width?: number
  height?: number
  format?: string
  image?: string
}

export const DEVICE_STATUS: Record<number, { label: string }> = {
  0: { label: '离线' },
  1: { label: '在线' },
  2: { label: '未激活' },
}

/** 后端统一响应包装 */
export interface ApiResult<T> {
  code: number
  message: string
  data: T
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

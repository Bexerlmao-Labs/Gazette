/** 云端 API 轻封装：统一解包 R<T>，非 0 抛错 */
import type { ApiResult, BitmapEncoding, CloudDevice, CloudScene, DeviceDisplay } from '@/types/cloud'

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(path, init)
  if (!res.ok) {
    throw new Error(`HTTP ${res.status}`)
  }
  const body = (await res.json()) as ApiResult<T>
  if (body.code !== 0) {
    throw new Error(body.message)
  }
  return body.data
}

function json(init?: RequestInit, body?: unknown): RequestInit {
  return {
    ...init,
    headers: { 'Content-Type': 'application/json', ...(init?.headers ?? {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
  }
}

/* ------------------------------------------------------------ 场景 */

export function listScenes() {
  return request<CloudScene[]>('/api/scene/list')
}

export function saveScene(scene: {
  id?: number
  name: string
  width?: number
  height?: number
  baseImageBase64?: string
  overlayDsl?: string
  bitmapEncoding?: BitmapEncoding
}) {
  return request<CloudScene>('/api/scene', json({ method: 'POST' }, scene))
}

export function deleteScene(id: number) {
  return request<void>(`/api/scene/${id}`, { method: 'DELETE' })
}

/** 渲染预览：返回 PNG blob 地址 */
export async function previewScene(id: number, data: Record<string, unknown> = {}) {
  const res = await fetch(`/api/scene/${id}/preview`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  })
  if (!res.ok) {
    throw new Error(`HTTP ${res.status}`)
  }
  return URL.createObjectURL(await res.blob())
}

/* ------------------------------------------------------------ 设备 */

export function listDevices() {
  return request<CloudDevice[]>('/api/device/list')
}

export function createDevice(device: {
  name: string
  model?: string
  width?: number
  height?: number
  currentSceneId?: number | null
}) {
  return request<CloudDevice>('/api/device', json({ method: 'POST' }, device))
}

export function updateDevice(
  id: number,
  req: { name?: string; currentSceneId?: number | null; bindScene: boolean },
) {
  return request<CloudDevice>(`/api/device/${id}`, json({ method: 'PUT' }, req))
}

export function deleteDevice(id: number) {
  return request<void>(`/api/device/${id}`, { method: 'DELETE' })
}

/** 用固件视角自测拉取接口 */
export function fetchDeviceDisplay(sn: string, key: string, hash?: string) {
  const q = new URLSearchParams({ sn, key })
  if (hash) q.set('hash', hash)
  return request<DeviceDisplay>(`/api/device/display?${q}`)
}

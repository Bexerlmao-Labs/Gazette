import { defineStore } from 'pinia'
import { ref } from 'vue'

/**
 * 应用功能开关：启动时从后端 /api/app-config 拉取。
 * 后端不可用时保持默认（蓝牙启用），不影响原有行为。
 */
export const useAppConfigStore = defineStore('app-config', () => {
  const loaded = ref(false)
  const bluetoothEnabled = ref(true)

  async function load() {
    try {
      const res = await fetch('/api/app-config')
      if (res.ok) {
        const body = await res.json()
        if (body.code === 0 && body.data) {
          bluetoothEnabled.value = body.data.bluetoothEnabled !== false
        }
      }
    } catch {
      // 忽略：后端不可用时按默认行为处理
    } finally {
      loaded.value = true
    }
  }

  return { loaded, bluetoothEnabled, load }
})

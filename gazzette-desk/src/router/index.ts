import { createRouter, createWebHistory } from 'vue-router'
import { useDisplayStore } from '@/stores/display'
import { useAppConfigStore } from '@/stores/app-config'
import ConnectView from '../views/ConnectView.vue'
import StudioView from '../views/StudioView.vue'
import CanvasView from '../views/CanvasView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/connect',
      name: 'connect',
      component: ConnectView,
    },
    {
      // 唯一主页面：像素画布 + 左侧组件库（StudioView 提供页头与弹窗布局）
      path: '/',
      component: StudioView,
      children: [{ path: '', name: 'canvas', component: CanvasView }],
    },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

/**
 * 蓝牙网关：未连接开发板时只能停留在连接页。
 * dev 环境（后端 bluetooth-enabled=false）不启用蓝牙连接页，直接进入主界面。
 */
router.beforeEach(async (to) => {
  const config = useAppConfigStore()
  if (!config.loaded) await config.load()

  // 蓝牙功能关闭：不进入连接页，也不再强制跳转
  if (!config.bluetoothEnabled) {
    return to.name === 'connect' ? { name: 'canvas' } : true
  }

  const store = useDisplayStore()
  if (!store.ble.connected && to.name !== 'connect') return { name: 'connect' }
  if (store.ble.connected && to.name === 'connect') return { name: 'canvas' }
  return true
})

export default router

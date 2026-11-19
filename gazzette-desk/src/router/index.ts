import { createRouter, createWebHistory } from 'vue-router'
import { useDisplayStore } from '@/stores/display'
import ConnectView from '../views/ConnectView.vue'
import StudioView from '../views/StudioView.vue'
import CanvasView from '../views/CanvasView.vue'
import ImageView from '../views/ImageView.vue'
import CloudView from '../views/CloudView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/connect',
      name: 'connect',
      component: ConnectView,
    },
    {
      path: '/',
      component: StudioView,
      children: [
        { path: '', name: 'canvas', component: CanvasView },
        { path: 'image', name: 'image', component: ImageView },
        { path: 'cloud', name: 'cloud', component: CloudView },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

/** 蓝牙网关：未连接开发板时只能停留在连接页；云端页面不依赖蓝牙，始终可访问 */
router.beforeEach((to) => {
  if (to.name === 'cloud') return true
  const store = useDisplayStore()
  if (!store.ble.connected && to.name !== 'connect') return { name: 'connect' }
  if (store.ble.connected && to.name === 'connect') return { name: 'canvas' }
  return true
})

export default router

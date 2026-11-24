<script setup lang="ts">
import { watch } from 'vue'
import { useRouter } from 'vue-router'
import PixelIcon from '@/components/PixelIcon.vue'
import { useDisplayStore } from '@/stores/display'

const store = useDisplayStore()
const router = useRouter()

// 连接成功后自动进入工作台
watch(
  () => store.ble.connected,
  (on) => {
    if (on) router.replace({ name: 'canvas' })
  },
)
</script>

<template>
  <div class="gate">
    <div class="gate__card">
      <div class="gate__mark">
        <PixelIcon name="bluetooth" :size="34" />
      </div>

      <h1 class="gate__title">PIXELINK</h1>
      <p class="gate__sub">墨水屏蓝牙工作台 · E-PAPER BLE STUDIO</p>

      <div class="gate__steps">
        <div class="gate__step">
          <span class="gate__num">1</span>
          <div>
            <strong>配对设备</strong>
            <p>先在电脑系统蓝牙设置中手动配对 ESP32 开发板</p>
          </div>
        </div>
        <div class="gate__step">
          <span class="gate__num">2</span>
          <div>
            <strong>网页连接</strong>
            <p>点击下方按钮，在浏览器弹窗中选择你的开发板</p>
          </div>
        </div>
        <div class="gate__step">
          <span class="gate__num">3</span>
          <div>
            <strong>同步尺寸并注册</strong>
            <p>连接后自动读取屏幕尺寸并向云端注册设备</p>
          </div>
        </div>
      </div>

      <p v-if="!store.ble.supported" class="gate__warn">
        当前浏览器不支持 Web Bluetooth，请使用 Chrome / Edge 打开本页面
      </p>

      <button
        class="btn btn--primary btn--block gate__btn"
        :disabled="store.ble.connecting || !store.ble.supported"
        @click="store.connectBluetooth()"
      >
        <PixelIcon name="bluetooth" :size="16" />
        {{ store.ble.connecting ? '连接中…' : '连接蓝牙设备' }}
      </button>

      <button
        v-if="store.ble.connected"
        class="btn btn--block gate__btn"
        @click="router.replace({ name: 'canvas' })"
      >
        进入工作台
      </button>

      <div v-if="store.ble.logs.length" class="gate__term">
        <p v-for="(line, i) in store.ble.logs.slice(-6)" :key="i">{{ line }}</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.gate {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px 16px;
}

.gate__card {
  width: 100%;
  max-width: 420px;
  padding: 28px 24px;
  border: 2px solid var(--ink);
  background: var(--paper-2);
  box-shadow: var(--shadow-lg);
}

.gate__mark {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 64px;
  height: 64px;
  margin-bottom: 16px;
  border: 2px solid var(--ink);
  background: var(--accent-soft);
  color: var(--ink);
  box-shadow: var(--shadow-sm);
}

.gate__title {
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 0.26em;
}

.gate__sub {
  margin-top: 6px;
  color: var(--muted);
  font-size: 9px;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

.gate__steps {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 22px;
}

.gate__step {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.gate__num {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  flex: none;
  border: 2px solid var(--ink);
  background: var(--paper-3);
  font-size: 10px;
  font-weight: 700;
}

.gate__step strong {
  font-size: 12px;
  letter-spacing: 0.06em;
}

.gate__step p {
  margin-top: 2px;
  color: var(--muted);
  font-size: 10px;
  line-height: 1.6;
}

.gate__warn {
  margin-top: 16px;
  padding: 8px 10px;
  border: 2px dashed var(--accent);
  color: var(--accent);
  font-size: 10px;
  letter-spacing: 0.04em;
}

.gate__btn {
  margin-top: 22px;
}

.gate__btn + .gate__btn {
  margin-top: 10px;
}

.gate__term {
  margin-top: 16px;
  padding: 10px;
  background: var(--board);
  border: 2px solid var(--ink);
  color: #c9c2ae;
  font-size: 9.5px;
  line-height: 1.7;
  overflow: hidden;
}

.gate__term p {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.gate__term p:last-child {
  color: var(--accent-2);
}
</style>

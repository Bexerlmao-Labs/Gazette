<script setup lang="ts">
import PixelIcon from '@/components/PixelIcon.vue'
import { useDisplayStore } from '@/stores/display'

const store = useDisplayStore()
</script>

<template>
  <header class="hd">
    <div class="hd__brand">
      <svg
        class="hd__mark"
        viewBox="0 0 8 8"
        width="34"
        height="34"
        shape-rendering="crispEdges"
        aria-hidden="true"
      >
        <rect width="8" height="8" fill="#1d1c18" />
        <rect x="1" y="1" width="2" height="2" fill="#df5430" />
        <rect x="4" y="1" width="1" height="1" fill="#f4efe3" />
        <rect x="5" y="2" width="2" height="2" fill="#ffc857" />
        <rect x="2" y="4" width="2" height="2" fill="#f4efe3" />
        <rect x="4" y="5" width="3" height="1" fill="#6fd3be" />
        <rect x="1" y="6" width="1" height="1" fill="#ffc857" />
      </svg>
      <span class="hd__names">
        <strong>PIXELINK</strong>
        <em>E-PAPER BLE STUDIO</em>
      </span>
    </div>

    <div class="hd__right">
      <div class="status" :class="{ 'is-on': store.ble.connected }">
        <i class="status__dot" />
        <span class="status__label">{{
          store.ble.connected ? store.ble.deviceName : '未连接'
        }}</span>
      </div>

      <button
        class="btn btn--ghost btn--sm"
        title="屏幕尺寸、颜色勾选与位图填充符"
        @click="store.openModal('settings')"
      >
        <PixelIcon name="gear" :size="14" />
        屏幕设置
      </button>

      <button class="btn btn--ghost btn--sm" @click="store.openModal('wifi')">
        <PixelIcon name="wifi" :size="14" />
        WiFi 配置
      </button>
      <button class="btn btn--ghost btn--sm" @click="store.openModal('export')">
        <PixelIcon name="download" :size="14" />
        导出
      </button>
      <button
        class="btn btn--primary"
        :disabled="store.ble.pushing || !store.ble.connected"
        @click="store.push()"
      >
        <PixelIcon name="send" :size="15" />
        {{ store.ble.pushing ? '推送中…' : '蓝牙推送' }}
      </button>
    </div>
  </header>
</template>

<style scoped>
.hd {
  position: sticky;
  top: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  gap: 18px;
  height: 68px;
  padding: 0 24px;
  border-bottom: 2px solid var(--ink);
  background: var(--paper);
}

.hd__brand {
  display: flex;
  align-items: center;
  gap: 12px;
}

.hd__mark {
  border: 2px solid var(--ink);
  box-shadow: var(--shadow-sm);
}

.hd__names {
  display: flex;
  flex-direction: column;
  line-height: 1.15;
}

.hd__names strong {
  font-size: 17px;
  font-weight: 700;
  letter-spacing: 0.22em;
}

.hd__names em {
  margin-top: 4px;
  color: var(--muted);
  font-size: 9px;
  font-style: normal;
  letter-spacing: 0.2em;
}

.hd__right {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}

.status {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 32px;
  padding: 0 10px;
  border: 2px solid var(--ink);
  background: var(--paper-2);
  font-size: 10px;
  letter-spacing: 0.1em;
}

.status__dot {
  width: 8px;
  height: 8px;
  background: var(--line);
  box-shadow: inset 0 0 0 1px var(--ink);
}

.status.is-on .status__dot {
  background: var(--good);
  box-shadow:
    inset 0 0 0 1px var(--ink),
    0 0 0 2px rgba(61, 122, 90, 0.18);
}

.status__label {
  max-width: 120px;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

@media (max-width: 1080px) {
  .hd {
    gap: 10px;
  }

  .hd__names em {
    display: none;
  }

}

@media (max-width: 860px) {
  .btn--ghost {
    width: 32px;
    padding: 0;
  }

  .btn--ghost:deep(svg) {
    margin: 0;
  }
}

@media (max-width: 720px) {
  .hd {
    height: 60px;
    padding: 0 14px;
  }

  .hd__names strong {
    font-size: 14px;
    letter-spacing: 0.16em;
  }

  .status__label {
    display: none;
  }

  .status {
    padding: 0 8px;
  }

  .hd__right .btn:not(.iconbtn):not(.btn--ghost) {
    padding: 0 10px;
    font-size: 10px;
  }
}
</style>

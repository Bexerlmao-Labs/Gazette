<script setup lang="ts">
import { ref } from 'vue'
import AppModal from '@/components/AppModal.vue'
import PixelIcon from '@/components/PixelIcon.vue'
import { useDisplayStore } from '@/stores/display'

const store = useDisplayStore()
const showPassword = ref(false)
</script>

<template>
  <AppModal title="WiFi 配置" subtitle="WRITE WIFI TO BOARD" @close="store.closeModal()">
    <p class="hint">
      此操作通过蓝牙把 WiFi 信息<strong>写入开发板</strong>，由开发板自行联网。网页本身不通过 WiFi
      连接设备。
    </p>

    <div class="field">
      <label class="label" for="wifi-ssid">WiFi 名称</label>
      <input
        id="wifi-ssid"
        v-model="store.wifi.ssid"
        class="input"
        placeholder="Studio-2.4G"
        spellcheck="false"
      />
    </div>

    <div class="field">
      <label class="label" for="wifi-pwd">WiFi 密码</label>
      <div class="pwd">
        <input
          id="wifi-pwd"
          v-model="store.wifi.password"
          class="input"
          :type="showPassword ? 'text' : 'password'"
          placeholder="••••••••"
          spellcheck="false"
        />
        <button
          class="pwd__btn"
          type="button"
          :title="showPassword ? '隐藏密码' : '显示密码'"
          @click="showPassword = !showPassword"
        >
          <PixelIcon :name="showPassword ? 'eyeOff' : 'eye'" :size="15" />
        </button>
      </div>
    </div>

    <button
      class="btn btn--primary btn--block"
      :disabled="store.wifiWriting || !store.ble.connected"
      @click="store.writeWifiConfig()"
    >
      <PixelIcon name="wifi" :size="15" />
      {{ store.wifiWriting ? '写入中…' : '通过蓝牙写入开发板' }}
    </button>

    <div v-if="store.ble.logs.length" class="term">
      <p v-for="(line, i) in store.ble.logs.slice(-5)" :key="i">{{ line }}</p>
    </div>
  </AppModal>
</template>

<style scoped>
.hint {
  color: var(--muted);
  font-size: 10px;
  line-height: 1.7;
  letter-spacing: 0.02em;
}

.hint strong {
  color: var(--ink-2);
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.pwd {
  position: relative;
}

.pwd .input {
  padding-right: 38px;
}

.pwd__btn {
  position: absolute;
  top: 2px;
  right: 2px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  color: var(--muted);
}

.pwd__btn:hover {
  color: var(--ink);
  background: var(--paper-3);
}

.term {
  padding: 10px;
  background: var(--board);
  border: 2px solid var(--ink);
  color: #c9c2ae;
  font-size: 9.5px;
  line-height: 1.7;
  overflow: hidden;
}

.term p {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.term p:last-child {
  color: var(--accent-2);
}
</style>

<script setup lang="ts">
import { computed, ref } from 'vue'
import AppModal from '@/components/AppModal.vue'
import PixelIcon from '@/components/PixelIcon.vue'
import { useDisplayStore } from '@/stores/display'

const store = useDisplayStore()
const format = ref<'hex' | 'json'>('hex')

const output = computed(() => (format.value === 'hex' ? store.hexText : store.jsonText))
const fileExt = computed(() => (format.value === 'hex' ? 'txt' : 'json'))

async function copy() {
  try {
    await navigator.clipboard.writeText(output.value)
    store.notify('像素数据已复制')
  } catch {
    const area = document.createElement('textarea')
    area.value = output.value
    document.body.appendChild(area)
    area.select()
    document.execCommand('copy')
    area.remove()
    store.notify('像素数据已复制')
  }
}

function download() {
  const blob = new Blob([output.value], { type: 'text/plain;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `pixelink-${store.width}x${store.height}.${fileExt.value}`
  a.click()
  URL.revokeObjectURL(url)
  store.notify('像素数据已导出')
}
</script>

<template>
  <AppModal title="导出数据" subtitle="EXPORT PIXEL DATA" @close="store.closeModal()">
    <div class="seg seg--full">
      <button :class="{ active: format === 'hex' }" @click="format = 'hex'">占位符文本</button>
      <button :class="{ active: format === 'json' }" @click="format = 'json'">JSON</button>
    </div>

    <pre class="code">{{ output }}</pre>

    <div class="meta">
      <span>{{ store.width }} × {{ store.height }}</span>
      <span>{{ store.filled }} PX 已点亮</span>
      <span>{{ store.byteSize }} B</span>
    </div>

    <div class="acts">
      <button class="btn btn--block" @click="copy">
        <PixelIcon name="copy" :size="14" />
        复制
      </button>
      <button class="btn btn--primary btn--block" @click="download">
        <PixelIcon name="download" :size="14" />
        下载文件
      </button>
    </div>
  </AppModal>
</template>

<style scoped>
.code {
  max-height: 220px;
  padding: 10px 12px;
  overflow: auto;
  border: 2px solid var(--ink);
  background: var(--board);
  color: #d6cfb9;
  font-size: 9.5px;
  line-height: 1.55;
  letter-spacing: 0.08em;
  white-space: pre;
  tab-size: 2;
}

.meta {
  display: flex;
  justify-content: space-between;
  padding: 0 2px;
  color: var(--muted);
  font-size: 9px;
  letter-spacing: 0.12em;
}

.acts {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}
</style>

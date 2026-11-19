<script setup lang="ts">
import { computed } from 'vue'
import PanelCard from '@/components/PanelCard.vue'
import DrawToolbar from '@/components/DrawToolbar.vue'
import PixelCanvas from '@/components/PixelCanvas.vue'
import { useDisplayStore } from '@/stores/display'

const store = useDisplayStore()

const TOOL_LABEL: Record<string, string> = {
  pen: '画笔',
  eraser: '橡皮',
  fill: '油漆桶',
  picker: '取色器',
}

const coordX = computed(() => (store.cursor ? String(store.cursor.x + 1).padStart(2, '0') : '--'))
const coordY = computed(() => (store.cursor ? String(store.cursor.y + 1).padStart(2, '0') : '--'))
</script>

<template>
  <PanelCard num="03" title="像素画布" subtitle="CANVAS">
    <template #action>
      <span class="live"><i />LIVE</span>
    </template>

    <DrawToolbar />
    <PixelCanvas />

    <div class="statusbar">
      <span class="statusbar__item">X {{ coordX }} · Y {{ coordY }}</span>
      <span class="statusbar__item">{{ store.width }} × {{ store.height }}</span>
      <span class="statusbar__item">{{ store.filled }} / {{ store.total }} PX</span>
      <span class="statusbar__item statusbar__item--tool">{{ TOOL_LABEL[store.tool] }}</span>
    </div>
  </PanelCard>
</template>

<style scoped>
.live {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 22px;
  padding: 0 8px;
  border: 1px dashed var(--line);
  color: var(--muted);
  font-size: 9px;
  letter-spacing: 0.16em;
}

.live i {
  width: 6px;
  height: 6px;
  background: var(--good);
  animation: blink 1.6s steps(2, end) infinite;
}

@keyframes blink {
  50% {
    opacity: 0.25;
  }
}

.statusbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding-top: 10px;
  border-top: 1px dashed var(--line);
  color: var(--muted);
  font-size: 10px;
  letter-spacing: 0.08em;
}

.statusbar__item--tool {
  color: var(--ink-2);
}

@media (max-width: 620px) {
  .statusbar {
    flex-wrap: wrap;
    gap: 4px 12px;
  }
}
</style>

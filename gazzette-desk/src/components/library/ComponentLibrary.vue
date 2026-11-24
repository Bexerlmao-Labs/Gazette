<script setup lang="ts">
import { computed, ref } from 'vue'
import PixelIcon from '@/components/PixelIcon.vue'
import { listComponents, type LibComponent } from './library'

const emit = defineEmits<{ preview: [comp: LibComponent] }>()

const components = ref<LibComponent[]>(listComponents())
const builtin = computed(() => components.value.filter((c) => !c.id.startsWith('plugin:')))
const plugins = computed(() => components.value.filter((c) => c.id.startsWith('plugin:')))

/* ------------------------------------------------------------- 拖拽 / 预览 */

function onDragStart(e: DragEvent, comp: LibComponent) {
  e.dataTransfer?.setData('lib-comp', comp.id)
  if (e.dataTransfer) e.dataTransfer.effectAllowed = 'copy'
}
</script>

<template>
  <aside class="clib">
    <div class="clib__title">组件库</div>

    <div class="clib__group">
      <div class="clib__group-label">内置组件</div>
      <div class="clib__grid">
        <button
          v-for="c in builtin"
          :key="c.id"
          class="comp"
          draggable="true"
          @dragstart="onDragStart($event, c)"
          @click="emit('preview', c)"
        >
          <PixelIcon :name="c.icon" :size="18" />
          <span class="comp__name">{{ c.name }}</span>
        </button>
      </div>
    </div>

    <div v-if="plugins.length" class="clib__group">
      <div class="clib__group-label">插件组件</div>
      <div class="clib__grid">
        <button
          v-for="c in plugins"
          :key="c.id"
          class="comp"
          draggable="true"
          @dragstart="onDragStart($event, c)"
          @click="emit('preview', c)"
        >
          <PixelIcon :name="c.icon" :size="18" />
          <span class="comp__name">{{ c.name }}</span>
        </button>
      </div>
    </div>
  </aside>
</template>

<style scoped>
.clib {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px 12px;
}

.clib__title {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.14em;
}

.clib__group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.clib__group-label {
  color: var(--muted);
  font-size: 9px;
  letter-spacing: 0.14em;
}

.clib__grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 6px;
}

.comp {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 100%;
  padding: 12px 6px 9px;
  border: 2px solid var(--ink);
  background: var(--paper);
  cursor: grab;
  text-align: center;
  transition:
    transform 0.08s ease,
    box-shadow 0.08s ease;
}

.comp:hover {
  transform: translate(-1px, -1px);
  box-shadow: 3px 3px 0 var(--accent);
}

.comp:active {
  cursor: grabbing;
}

.comp__name {
  font-size: 10px;
  letter-spacing: 0.04em;
  color: var(--ink);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 100%;
}

@media (max-width: 900px) {
  .clib {
    padding: 14px;
  }
}
</style>

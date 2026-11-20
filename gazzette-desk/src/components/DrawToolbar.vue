<script setup lang="ts">
import PixelIcon from '@/components/PixelIcon.vue'
import { useDisplayStore, type Tool } from '@/stores/display'

const store = useDisplayStore()

const TOOLS: { key: Tool; label: string; icon: string }[] = [
  { key: 'pen', label: '画笔', icon: 'pen' },
  { key: 'eraser', label: '橡皮', icon: 'eraser' },
  { key: 'rect', label: '矩形', icon: 'rect' },
  { key: 'fill', label: '填充', icon: 'fill' },
  { key: 'picker', label: '取色', icon: 'picker' },
]

function pickColor(hex: string) {
  store.color = hex
  if (store.tool === 'eraser' || store.tool === 'picker') store.tool = 'pen'
}
</script>

<template>
  <div class="tools">
    <div class="tools__row">
      <button
        v-for="item in TOOLS"
        :key="item.key"
        class="tool"
        :class="{ active: store.tool === item.key }"
        :title="item.label"
        @click="store.tool = item.key"
      >
        <PixelIcon :name="item.icon" :size="15" />
        <span>{{ item.label }}</span>
      </button>
    </div>

    <div class="tools__row tools__row--acts">
      <button class="iconbtn" :disabled="!store.canUndo" title="撤销" @click="store.undo()">
        <PixelIcon name="undo" :size="15" />
      </button>
      <button class="iconbtn" :disabled="!store.canRedo" title="重做" @click="store.redo()">
        <PixelIcon name="redo" :size="15" />
      </button>
      <button class="iconbtn" title="反色" @click="store.invert()">
        <PixelIcon name="invert" :size="15" />
      </button>
      <button class="iconbtn" title="清空画布" @click="store.clear()">
        <PixelIcon name="trash" :size="15" />
      </button>
    </div>

    <div class="palette">
      <div class="palette__head">
        <span class="label palette__label">墨水颜色</span>
        <code class="palette__code">{{ store.color.toUpperCase() }}</code>
        <button class="palette__edit" title="屏幕尺寸、颜色勾选与位图填充符" @click="store.openModal('settings')">
          <PixelIcon name="gear" :size="13" />
          <span>屏幕设置</span>
        </button>
      </div>
      <div class="swatches">
        <button
          v-for="c in store.activePalette"
          :key="c.hex + c.char"
          class="swatch"
          :class="{ active: store.color === c.hex }"
          :style="{ background: c.hex }"
          :title="`${c.name} ${c.hex} · 占位符 ${c.char}`"
          @click="pickColor(c.hex)"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.tools {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-bottom: 12px;
  border-bottom: 1px dashed var(--line);
}

.tools__row {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 8px;
}

.tools__row--acts {
  display: flex;
  gap: 6px;
}

.tool {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 32px;
  border: 2px solid var(--line);
  background: var(--paper);
  color: var(--ink-2);
  font-size: 10px;
  letter-spacing: 0.08em;
  transition:
    background 0.12s ease,
    border-color 0.12s ease,
    color 0.12s ease;
}

.tool:hover {
  border-color: var(--ink);
}

.tool.active {
  border-color: var(--ink);
  background: var(--ink);
  color: var(--paper);
  box-shadow: var(--shadow-sm);
}

.acts {
  display: flex;
  gap: 6px;
}

.palette {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.palette__head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.palette__label {
  margin: 0;
}

.palette__code {
  color: var(--muted);
  font-size: 9px;
  letter-spacing: 0.1em;
}

.palette__edit {
  display: flex;
  align-items: center;
  gap: 5px;
  margin-left: auto;
  height: 26px;
  padding: 0 8px;
  border: 2px solid var(--ink);
  background: var(--paper);
  color: var(--ink-2);
  font-size: 9.5px;
  letter-spacing: 0.08em;
  cursor: pointer;
  transition:
    background 0.12s ease,
    color 0.12s ease;
}

.palette__edit:hover {
  background: var(--ink);
  color: var(--paper);
}

.swatches {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.swatch {
  width: 20px;
  height: 20px;
  border: 2px solid var(--ink);
  transition: transform 0.08s ease;
}

.swatch:hover {
  transform: translateY(-2px);
}

.swatch.active {
  box-shadow:
    0 0 0 2px var(--paper-2),
    0 0 0 4px var(--ink);
}

@media (max-width: 520px) {
  .tools__row {
    grid-template-columns: repeat(3, 1fr);
  }
}
</style>

<script setup lang="ts">
import { computed } from 'vue'
import PixelIcon from '@/components/PixelIcon.vue'
import PixelCanvas from '@/components/PixelCanvas.vue'
import { fitPlacedText } from '@/components/library/library'
import { useDisplayStore } from '@/stores/display'

const store = useDisplayStore()

const TOOL_LABEL: Record<string, string> = {
  pen: '画笔',
  eraser: '橡皮',
  rect: '矩形',
  line: '直线',
  fill: '填充',
}

const coordX = computed(() => (store.cursor ? String(store.cursor.x + 1).padStart(2, '0') : '--'))
const coordY = computed(() => (store.cursor ? String(store.cursor.y + 1).padStart(2, '0') : '--'))

/** 当前选中的放置组件 */
const selectedPlaced = computed(
  () => store.placedComponents.find((p) => p.uid === store.selectedPlacedUid) ?? null,
)
/** 是否有可编辑的文本字段 */
const hasTextField = computed(
  () => selectedPlaced.value?.comp.kind === 'text' || selectedPlaced.value?.comp.kind === 'qr',
)
const isDatetime = computed(() => selectedPlaced.value?.comp.kind === 'datetime')

const TEXT_PLACEHOLDER: Record<string, string> = {
  text: '输入文本',
  qr: '输入二维码链接',
}

/** 文本/时间内容变长时自动扩大组件框，避免点阵被降采样发糊 */
function fitSelected() {
  if (selectedPlaced.value) fitPlacedText(selectedPlaced.value)
}

function removeSelected() {
  if (selectedPlaced.value) store.removePlaced(selectedPlaced.value.uid)
}
</script>

<template>
  <div class="cpanel">
    <!-- 选中放置组件：文本编辑条（画布上方，顶栏之下） -->
    <div v-if="selectedPlaced" class="placed-editor">
      <span class="placed-editor__name">
        <PixelIcon :name="selectedPlaced.comp.icon" :size="12" />
        {{ selectedPlaced.comp.name }}
      </span>
      <input
        v-if="hasTextField"
        v-model="selectedPlaced.comp.template"
        class="input placed-editor__input"
        :placeholder="TEXT_PLACEHOLDER[selectedPlaced.comp.kind]"
        @input="fitSelected"
      />
      <input
        v-else-if="isDatetime"
        v-model="selectedPlaced.comp.attrs.format"
        class="input placed-editor__input placed-editor__input--short"
        placeholder="%H:%M"
        @input="fitSelected"
      />
      <button class="btn btn--ghost btn--sm" @click="removeSelected">
        <PixelIcon name="trash" :size="12" />
      </button>
    </div>

    <PixelCanvas />

    <div class="statusbar">
      <span class="statusbar__item">X {{ coordX }} · Y {{ coordY }}</span>
      <span class="statusbar__item">{{ store.width }} × {{ store.height }}</span>
      <span class="statusbar__item">{{ store.filled }} / {{ store.total }} PX</span>
      <span class="statusbar__item statusbar__item--tool">{{ TOOL_LABEL[store.tool] }}</span>
    </div>
  </div>
</template>

<style scoped>
.cpanel {
  flex: 1;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.placed-editor {
  flex: none;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 12px;
  border-bottom: 2px solid var(--ink);
  background: var(--paper-2);
}

.placed-editor__name {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  flex: none;
  color: var(--ink-2);
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.06em;
}

.placed-editor__input {
  flex: 1;
  min-width: 0;
  height: 28px;
}

.placed-editor__input--short {
  flex: none;
  width: 120px;
}

.statusbar {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  height: 34px;
  padding: 0 16px;
  border-top: 2px solid var(--ink);
  background: var(--paper-2);
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

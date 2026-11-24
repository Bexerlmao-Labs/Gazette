<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import AppModal from '@/components/AppModal.vue'
import PixelIcon from '@/components/PixelIcon.vue'
import { useDisplayStore } from '@/stores/display'
import { ensureFontLoaded } from './font'
import { placeComponent, renderToCells, textContentSize, type LibComponent } from './library'

const props = defineProps<{ comp: LibComponent }>()
const emit = defineEmits<{ close: [] }>()

const store = useDisplayStore()
const canvasRef = ref<HTMLCanvasElement | null>(null)

/** 本地可编辑副本：直接改内容不影响组件库定义 */
const editable = reactive<LibComponent>({
  ...props.comp,
  attrs: { ...props.comp.attrs },
})

const isText = computed(() => editable.kind === 'text')
const isDatetime = computed(() => editable.kind === 'datetime')
const isQr = computed(() => editable.kind === 'qr')

/** 文本/时间内容变化时按内容自适应尺寸（避免点阵被降采样发糊） */
function fitContentSize() {
  const size = textContentSize(editable)
  if (!size) return
  editable.w = Math.min(size.w + 2, store.width)
  editable.h = Math.min(size.h + 2, store.height)
}

const pattern = computed(() => renderToCells(editable))
const scale = computed(() => Math.max(2, Math.min(8, Math.floor(280 / Math.max(1, editable.w)))))

function draw() {
  const canvas = canvasRef.value
  if (!canvas) return
  const w = editable.w
  const h = editable.h
  const s = scale.value
  canvas.width = w * s
  canvas.height = h * s
  const ctx = canvas.getContext('2d')
  if (!ctx) return

  ctx.fillStyle = store.bgColor
  ctx.fillRect(0, 0, canvas.width, canvas.height)
  ctx.fillStyle = store.darkestColor.hex

  const gap = s >= 6 ? 1 : 0
  const cells = pattern.value
  for (let y = 0; y < h; y++) {
    for (let x = 0; x < w; x++) {
      if (cells[y * w + x] !== '#') continue
      ctx.fillRect(x * s + gap, y * s + gap, s - gap * 2, s - gap * 2)
    }
  }
}

function placeCenter() {
  void placeComponent(
    editable,
    Math.max(0, Math.floor((store.width - editable.w) / 2)),
    Math.max(0, Math.floor((store.height - editable.h) / 2)),
  )
  emit('close')
}

onMounted(() => {
  fitContentSize()
  draw()
  // 字库异步加载完成后重绘（文字点阵来自后端字库）
  void ensureFontLoaded().then((f) => {
    if (f) {
      fitContentSize()
      draw()
    }
  })
})
watch(
  () => [editable.template, editable.attrs.format, editable.w, editable.h, editable.id],
  () => draw(),
  { flush: 'post' },
)
watch(
  () => [editable.template, editable.attrs.format],
  () => fitContentSize(),
)
</script>

<template>
  <AppModal :title="`预览 · ${comp.name}`" subtitle="COMPONENT PREVIEW" @close="emit('close')">
    <div class="pv">
      <div class="pv__frame">
        <canvas ref="canvasRef" />
      </div>
      <div v-if="isText" class="field">
        <label class="label" for="pv-text">内容</label>
        <input
          id="pv-text"
          v-model="editable.template"
          class="input"
          placeholder="输入文本"
          spellcheck="false"
        />
      </div>
      <div v-else-if="isQr" class="field">
        <label class="label" for="pv-qr">链接</label>
        <input
          id="pv-qr"
          v-model="editable.template"
          class="input"
          placeholder="https://"
          spellcheck="false"
        />
      </div>
      <div v-else-if="isDatetime" class="field">
        <label class="label" for="pv-fmt">格式</label>
        <input
          id="pv-fmt"
          v-model="editable.attrs.format"
          class="input"
          placeholder="%H:%M"
          spellcheck="false"
        />
      </div>
      <div class="pv__actions">
        <button class="btn btn--primary" @click="placeCenter">
          <PixelIcon name="send" :size="14" />
          放到画布中心
        </button>
        <button class="btn btn--ghost" @click="emit('close')">关闭</button>
      </div>
    </div>
  </AppModal>
</template>

<style scoped>
.pv {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.pv__frame {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 14px;
  border: 2px solid var(--ink);
  background: var(--board);
}

.pv__frame canvas {
  image-rendering: pixelated;
  max-width: 100%;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.label {
  color: var(--ink-2);
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.1em;
}

.pv__actions {
  display: flex;
  gap: 8px;
}
</style>

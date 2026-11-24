<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import AppModal from '@/components/AppModal.vue'
import PixelIcon from '@/components/PixelIcon.vue'
import { useDisplayStore } from '@/stores/display'
import { ensureFontLoaded } from './font'
import { placeComponent, renderToCells, type LibComponent } from './library'

const props = defineProps<{ comp: LibComponent }>()
const emit = defineEmits<{ close: [] }>()

const store = useDisplayStore()
const canvasRef = ref<HTMLCanvasElement | null>(null)

const pattern = computed(() => renderToCells(props.comp))
const scale = computed(() => Math.max(2, Math.min(8, Math.floor(280 / Math.max(1, props.comp.w)))))

function draw() {
  const canvas = canvasRef.value
  if (!canvas) return
  const w = props.comp.w
  const h = props.comp.h
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
    props.comp,
    Math.floor((store.width - props.comp.w) / 2),
    Math.floor((store.height - props.comp.h) / 2),
  )
  emit('close')
}

onMounted(() => {
  draw()
  // 字库异步加载完成后重绘（文字点阵来自后端字库）
  void ensureFontLoaded().then((f) => {
    if (f) draw()
  })
})
watch(() => props.comp.id, draw, { flush: 'post' })
</script>

<template>
  <AppModal :title="`预览 · ${comp.name}`" subtitle="COMPONENT PREVIEW" @close="emit('close')">
    <div class="pv">
      <div class="pv__frame">
        <canvas ref="canvasRef" />
      </div>
      <div class="pv__meta">
        <div><span>尺寸</span><code class="mono">{{ comp.w }} × {{ comp.h }} px</code></div>
        <div v-if="comp.template"><span>模板</span><code class="mono">{{ comp.template }}</code></div>
        <div v-if="comp.kind === 'datetime'"><span>格式</span><code class="mono">{{ comp.attrs.format }}</code></div>
        <p class="pv__desc">{{ comp.desc }}</p>
      </div>
      <p class="pv__hint">把组件从左侧库拖到画布上放置；墨水色以外区域为透明，不覆盖已有像素。</p>
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

.pv__meta {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.pv__meta > div {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.pv__meta span {
  flex: none;
  width: 34px;
  color: var(--muted);
  font-size: 9px;
  letter-spacing: 0.12em;
}

.pv__meta code {
  font-size: 10px;
  word-break: break-all;
}

.pv__desc {
  color: var(--muted);
  font-size: 9.5px;
  line-height: 1.7;
}

.pv__hint {
  padding: 8px 10px;
  border: 1px dashed var(--line);
  color: var(--muted);
  font-size: 9.5px;
  line-height: 1.7;
}

.pv__actions {
  display: flex;
  gap: 8px;
}
</style>

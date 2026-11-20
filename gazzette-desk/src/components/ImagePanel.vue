<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import PanelCard from '@/components/PanelCard.vue'
import PixelIcon from '@/components/PixelIcon.vue'
import { useDisplayStore } from '@/stores/display'
import { convertPixels, type ConvertOptions } from '@/utils/convert'

const store = useDisplayStore()
const router = useRouter()

const fileInput = ref<HTMLInputElement | null>(null)
const previewRef = ref<HTMLCanvasElement | null>(null)
const sourceUrl = ref('')
const fileName = ref('')
const fileSize = ref(0)
const sourceSize = ref({ w: 0, h: 0 })
const dragging = ref(false)
const previewCells = ref<string[] | null>(null)

let source: HTMLImageElement | null = null
let sampler: HTMLCanvasElement | null = null

const options = reactive<ConvertOptions>({
  brightness: 0,
  contrast: 0,
  dither: true,
  invert: false,
})

const sizeLabel = computed(() => {
  const kb = fileSize.value / 1024
  return kb >= 1024 ? `${(kb / 1024).toFixed(1)} MB` : `${Math.max(1, Math.round(kb))} KB`
})

function handleFile(file: File) {
  if (!file.type.startsWith('image/')) {
    store.notify('请选择 PNG / JPG 等图片文件')
    return
  }
  if (sourceUrl.value) URL.revokeObjectURL(sourceUrl.value)

  sourceUrl.value = URL.createObjectURL(file)
  fileName.value = file.name
  fileSize.value = file.size

  const img = new Image()
  img.onload = () => {
    source = img
    sourceSize.value = { w: img.naturalWidth, h: img.naturalHeight }
    updatePreview()
  }
  img.onerror = () => store.notify('图片解析失败')
  img.src = sourceUrl.value
}

function onPick(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (file) handleFile(file)
  input.value = ''
}

function onDrop(event: DragEvent) {
  dragging.value = false
  const file = event.dataTransfer?.files?.[0]
  if (file) handleFile(file)
}

function updatePreview() {
  if (!source) {
    previewCells.value = null
    return
  }
  const w = store.width
  const h = store.height

  if (!sampler) sampler = document.createElement('canvas')
  sampler.width = w
  sampler.height = h
  const ctx = sampler.getContext('2d', { willReadFrequently: true })
  if (!ctx) return

  ctx.clearRect(0, 0, w, h)
  ctx.imageSmoothingEnabled = true
  ctx.imageSmoothingQuality = 'high'
  ctx.drawImage(source, 0, 0, w, h)

  const data = ctx.getImageData(0, 0, w, h).data
  previewCells.value = convertPixels(
    data,
    w,
    h,
    options,
    store.activePalette.map((c) => c.hex),
  )
}

function drawPreview() {
  const canvas = previewRef.value
  if (!canvas) return
  const w = store.width
  const h = store.height
  const scale = Math.max(2, Math.min(9, Math.floor(420 / w)))
  canvas.width = w * scale
  canvas.height = h * scale

  const ctx = canvas.getContext('2d')
  if (!ctx) return

  ctx.fillStyle = store.bgColor
  ctx.fillRect(0, 0, canvas.width, canvas.height)

  const cells = previewCells.value
  if (!cells) return

  const gap = scale >= 7 ? 1 : 0
  for (let y = 0; y < h; y++) {
    for (let x = 0; x < w; x++) {
      const color = cells[y * w + x]
      if (!color) continue
      ctx.fillStyle = color
      ctx.fillRect(x * scale + gap, y * scale + gap, scale - gap * 2, scale - gap * 2)
    }
  }
}

function apply() {
  if (!previewCells.value) {
    store.notify('请先上传一张照片')
    return
  }
  store.loadCells(previewCells.value, store.width, store.height)
  store.notify('照片已转换为墨水屏颜色并写入画布')
  router.push({ name: 'canvas' })
}

watch(previewCells, drawPreview, { flush: 'post' })

watch(
  [
    () => options.brightness,
    () => options.contrast,
    () => options.dither,
    () => options.invert,
    () => store.width,
    () => store.height,
    () => store.activePalette.map((c) => c.hex).join(','),
  ],
  updatePreview,
)

onBeforeUnmount(() => {
  if (sourceUrl.value) URL.revokeObjectURL(sourceUrl.value)
})
</script>

<template>
  <PanelCard num="02" title="照片转换" subtitle="PHOTO → E-PAPER INK">
    <div
      class="drop"
      :class="{ 'is-drag': dragging, 'has-image': !!sourceUrl }"
      @click="fileInput?.click()"
      @dragover.prevent="dragging = true"
      @dragleave.prevent="dragging = false"
      @drop.prevent="onDrop"
    >
      <input ref="fileInput" type="file" accept="image/*" hidden @change="onPick" />

      <template v-if="sourceUrl">
        <img class="drop__thumb" :src="sourceUrl" alt="已上传照片预览" />
        <div class="drop__info">
          <strong class="drop__name">{{ fileName }}</strong>
          <span class="drop__meta">{{ sourceSize.w }} × {{ sourceSize.h }} · {{ sizeLabel }}</span>
          <span class="drop__link">点击更换照片</span>
        </div>
      </template>

      <template v-else>
        <PixelIcon name="upload" :size="26" />
        <strong class="drop__name">拖入照片，或点击上传</strong>
        <span class="drop__meta">任意照片 · PNG · JPG · WEBP</span>
      </template>
    </div>

    <p class="note">
      任意颜色的照片会自动匹配为「墨水屏设置」中启用的颜色 （当前
      {{ store.activePalette.map((c) => c.name).join(' / ') }}）
    </p>

    <div class="sliders">
      <label class="slider">
        <span class="slider__key">亮度</span>
        <input v-model.number="options.brightness" type="range" min="-100" max="100" step="1" />
        <code class="slider__val">{{ options.brightness }}</code>
      </label>
      <label class="slider">
        <span class="slider__key">对比度</span>
        <input v-model.number="options.contrast" type="range" min="-100" max="100" step="1" />
        <code class="slider__val">{{ options.contrast }}</code>
      </label>
    </div>

    <div class="checks">
      <label class="check">
        <input v-model="options.dither" type="checkbox" />
        <span>抖动过渡</span>
      </label>
      <label class="check">
        <input v-model="options.invert" type="checkbox" />
        <span>反色</span>
      </label>
      <span class="checks__size">{{ store.width }} × {{ store.height }}</span>
    </div>

    <div class="preview">
      <div class="preview__head">
        <span>转换预览</span>
        <span>目标屏幕 {{ store.width }} × {{ store.height }}</span>
      </div>
      <div class="preview__frame">
        <canvas ref="previewRef" />
        <span v-if="!sourceUrl" class="preview__empty">尚未选择照片</span>
      </div>
    </div>

    <button class="btn btn--primary btn--block" :disabled="!sourceUrl" @click="apply">
      <PixelIcon name="image" :size="15" />
      转换并写入画布
    </button>
  </PanelCard>
</template>

<style scoped>
.drop {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 150px;
  padding: 16px;
  border: 2px dashed var(--ink);
  background-color: var(--paper);
  background-image:
    linear-gradient(45deg, rgba(29, 28, 24, 0.04) 25%, transparent 25%),
    linear-gradient(-45deg, rgba(29, 28, 24, 0.04) 25%, transparent 25%),
    linear-gradient(45deg, transparent 75%, rgba(29, 28, 24, 0.04) 75%),
    linear-gradient(-45deg, transparent 75%, rgba(29, 28, 24, 0.04) 75%);
  background-size: 12px 12px;
  background-position:
    0 0,
    0 6px,
    6px -6px,
    -6px 0;
  color: var(--muted);
  text-align: center;
  cursor: pointer;
  transition:
    border-color 0.12s ease,
    background-color 0.12s ease;
}

.drop:hover,
.drop.is-drag {
  border-color: var(--accent);
  color: var(--ink);
}

.drop.is-drag {
  background-color: var(--accent-soft);
}

.drop.has-image {
  flex-direction: row;
  justify-content: flex-start;
  gap: 12px;
  min-height: 96px;
  padding: 12px;
  border-style: solid;
  text-align: left;
}

.drop__thumb {
  width: 64px;
  height: 64px;
  flex: none;
  border: 2px solid var(--ink);
  object-fit: cover;
}

.drop__info {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.drop__name {
  color: var(--ink);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.02em;
  word-break: break-all;
}

.drop__meta {
  font-size: 9px;
  letter-spacing: 0.1em;
}

.drop__link {
  margin-top: 2px;
  color: var(--accent);
  font-size: 9px;
  letter-spacing: 0.12em;
}

.note {
  padding: 8px 10px;
  border: 1px dashed var(--line);
  color: var(--muted);
  font-size: 9.5px;
  line-height: 1.7;
  letter-spacing: 0.02em;
}

.sliders {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.slider {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr) 34px;
  align-items: center;
  gap: 8px;
}

.slider__key {
  color: var(--muted);
  font-size: 10px;
  letter-spacing: 0.08em;
}

.slider__val {
  color: var(--ink-2);
  font-size: 10px;
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.checks {
  display: flex;
  align-items: center;
  gap: 14px;
}

.checks__size {
  margin-left: auto;
  color: var(--muted);
  font-size: 9px;
  letter-spacing: 0.1em;
}

.preview {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.preview__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--muted);
  font-size: 9px;
  letter-spacing: 0.12em;
}

.preview__frame {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 10px;
  min-height: 120px;
  border: 2px solid var(--ink);
  background: var(--board);
}

.preview__frame canvas {
  width: 100%;
  height: auto;
  max-height: 300px;
  object-fit: contain;
  image-rendering: pixelated;
}

.preview__empty {
  position: absolute;
  color: #857e6d;
  font-size: 10px;
  letter-spacing: 0.16em;
}
</style>

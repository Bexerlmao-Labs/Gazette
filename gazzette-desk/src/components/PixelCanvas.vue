<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useDisplayStore } from '@/stores/display'

const store = useDisplayStore()

const wrapEl = ref<HTMLDivElement | null>(null)
const canvasEl = ref<HTMLCanvasElement | null>(null)
const stageW = ref(0)
const stageH = ref(0)
const cellPx = ref(8)
const hover = ref<{ x: number; y: number } | null>(null)

let ctx: CanvasRenderingContext2D | null = null
let cell = 8
let painting = false
let last: { x: number; y: number } | null = null
let lastWidth = -1
let observer: ResizeObserver | null = null

const BOARD_BG = '#1b1b18'
const DOT_COLOR = '#33322b'

const cursorStyle = computed(() => {
  if (!hover.value) return {}
  return {
    left: `${hover.value.x * cellPx.value}px`,
    top: `${hover.value.y * cellPx.value}px`,
    width: `${cellPx.value}px`,
    height: `${cellPx.value}px`,
  }
})

function readCellSize(): number {
  const wrap = wrapEl.value
  if (!wrap) return 8
  const maxWidth = wrap.clientWidth
  if (!maxWidth) return 8
  const maxHeight = Math.max(200, Math.min(window.innerHeight - 340, 520))
  return Math.max(2, Math.floor(Math.min(maxWidth / store.width, maxHeight / store.height)))
}

function layout() {
  const wrap = wrapEl.value
  const canvas = canvasEl.value
  if (!wrap || !canvas) return

  cell = readCellSize()
  const bw = cell * store.width
  const bh = cell * store.height
  stageW.value = bw
  stageH.value = bh
  cellPx.value = cell

  const dpr = Math.min(window.devicePixelRatio || 1, 2)
  canvas.width = Math.round(bw * dpr)
  canvas.height = Math.round(bh * dpr)
  canvas.style.width = `${bw}px`
  canvas.style.height = `${bh}px`

  ctx = canvas.getContext('2d')
  ctx?.setTransform(dpr, 0, 0, dpr, 0, 0)
  render()
}

function roundRectPath(
  c: CanvasRenderingContext2D,
  x: number,
  y: number,
  size: number,
  radius: number,
) {
  const r = Math.min(radius, size / 2)
  c.beginPath()
  c.moveTo(x + r, y)
  c.lineTo(x + size - r, y)
  c.lineTo(x + size - r, y + r)
  c.lineTo(x + size, y + r)
  c.lineTo(x + size, y + size - r)
  c.lineTo(x + size - r, y + size - r)
  c.lineTo(x + size - r, y + size)
  c.lineTo(x + r, y + size)
  c.lineTo(x + r, y + size - r)
  c.lineTo(x, y + size - r)
  c.lineTo(x, y + r)
  c.lineTo(x + r, y + r)
  c.closePath()
}

function render() {
  if (!ctx) return
  const w = store.width
  const h = store.height

  ctx.clearRect(0, 0, stageW.value, stageH.value)
  ctx.fillStyle = BOARD_BG
  ctx.fillRect(0, 0, stageW.value, stageH.value)

  const gap = cell >= 5 ? 1 : 0
  const isRound = store.shape === 'round'
  const radius = Math.max(1, Math.round(cell * 0.14))
  const dotSize = Math.max(1, Math.min(2, Math.round(cell * 0.14)))

  for (let y = 0; y < h; y++) {
    for (let x = 0; x < w; x++) {
      const color = store.cells[y * w + x]
      const px = x * cell + gap
      const py = y * cell + gap
      const size = cell - gap * 2

      if (color) {
        ctx.fillStyle = color
        if (isRound) {
          ctx.beginPath()
          ctx.arc(px + size / 2, py + size / 2, size / 2, 0, Math.PI * 2)
          ctx.fill()
        } else if (radius > 1) {
          roundRectPath(ctx, px, py, size, radius)
          ctx.fill()
        } else {
          ctx.fillRect(px, py, size, size)
        }
      } else if (cell >= 6) {
        ctx.fillStyle = DOT_COLOR
        const offset = (cell - dotSize) / 2
        ctx.fillRect(x * cell + offset, y * cell + offset, dotSize, dotSize)
      }
    }
  }
}

function pickCell(event: PointerEvent) {
  const canvas = canvasEl.value
  if (!canvas) return null
  const rect = canvas.getBoundingClientRect()
  const x = Math.floor((event.clientX - rect.left) / cell)
  const y = Math.floor((event.clientY - rect.top) / cell)
  if (x < 0 || y < 0 || x >= store.width || y >= store.height) return null
  return { x, y }
}

function applyBrush(x: number, y: number) {
  const color = store.tool === 'eraser' ? '' : store.color
  const size = store.brush
  const startX = x - Math.floor(size / 2)
  const startY = y - Math.floor(size / 2)
  for (let dy = 0; dy < size; dy++) {
    for (let dx = 0; dx < size; dx++) {
      store.setCell(startX + dx, startY + dy, color)
    }
  }
}

function onPointerDown(event: PointerEvent) {
  if (event.pointerType === 'mouse' && event.button !== 0) return
  const point = pickCell(event)
  if (!point) return

  canvasEl.value?.setPointerCapture(event.pointerId)
  hover.value = point
  store.cursor = point

  if (store.tool === 'picker') {
    const color = store.cells[point.y * store.width + point.x]
    if (color) {
      store.color = color
      store.tool = 'pen'
      store.notify(`已取色 ${color.toUpperCase()}`)
    }
    return
  }

  store.pushUndo()

  if (store.tool === 'fill') {
    store.floodFill(point.x, point.y, store.color)
    return
  }

  painting = true
  last = point
  applyBrush(point.x, point.y)
}

function onPointerMove(event: PointerEvent) {
  const point = pickCell(event)
  hover.value = point
  store.cursor = point

  if (!painting || !point || store.tool === 'fill' || store.tool === 'picker') return

  const from = last ?? point
  const dx = point.x - from.x
  const dy = point.y - from.y
  const steps = Math.max(Math.abs(dx), Math.abs(dy))
  for (let s = 1; s <= steps; s++) {
    applyBrush(from.x + Math.round((dx * s) / steps), from.y + Math.round((dy * s) / steps))
  }
  last = point
}

function onPointerUp(event: PointerEvent) {
  painting = false
  last = null
  const canvas = canvasEl.value
  if (canvas?.hasPointerCapture(event.pointerId)) {
    canvas.releasePointerCapture(event.pointerId)
  }
}

function onPointerLeave() {
  if (painting) return
  hover.value = null
  store.cursor = null
}

function handleWindowResize() {
  layout()
}

watch(
  () => [store.width, store.height, store.shape],
  () => layout(),
  { flush: 'post' },
)

watch(() => store.revision, render, { flush: 'post' })

onMounted(() => {
  layout()
  lastWidth = wrapEl.value?.clientWidth ?? -1
  if (wrapEl.value) {
    observer = new ResizeObserver(() => {
      const width = wrapEl.value?.clientWidth ?? 0
      if (width === lastWidth) return
      lastWidth = width
      layout()
    })
    observer.observe(wrapEl.value)
  }
  window.addEventListener('resize', handleWindowResize)
})

onBeforeUnmount(() => {
  observer?.disconnect()
  observer = null
  window.removeEventListener('resize', handleWindowResize)
})
</script>

<template>
  <div ref="wrapEl" class="board">
    <div class="board__stage" :style="{ width: `${stageW}px`, height: `${stageH}px` }">
      <canvas
        ref="canvasEl"
        @pointerdown="onPointerDown"
        @pointermove="onPointerMove"
        @pointerup="onPointerUp"
        @pointercancel="onPointerUp"
        @pointerleave="onPointerLeave"
        @contextmenu.prevent
      />
      <span v-if="hover" class="board__cursor" :style="cursorStyle" />
    </div>
  </div>
</template>

<style scoped>
.board {
  display: flex;
  justify-content: center;
  padding: 2px 8px 8px 2px;
}

.board__stage {
  position: relative;
  outline: 2px solid var(--ink);
  background: var(--board);
  box-shadow: var(--shadow-lg);
}

canvas {
  display: block;
  image-rendering: pixelated;
  touch-action: none;
  cursor: crosshair;
}

.board__cursor {
  position: absolute;
  pointer-events: none;
  outline: 2px solid var(--accent);
  outline-offset: -2px;
  background: rgba(223, 84, 48, 0.12);
}
</style>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useDisplayStore } from '@/stores/display'
import { commitPlaced, getLibComponent, placeComponent, placedPattern, type PlacedComponent } from '@/components/library/library'

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
/** 矩形/直线工具：起点与预览像素 */
const rectStart = ref<{ x: number; y: number } | null>(null)
const rectPreview = ref<{ x: number; y: number }[] | null>(null)
const lineStart = ref<{ x: number; y: number } | null>(null)
const linePreview = ref<{ x: number; y: number }[] | null>(null)
/** 放置组件交互态 */
let moveState: { uid: string; startPX: number; startPY: number; origX: number; origY: number } | null = null
let resizeState: { uid: string; startPX: number; startPY: number; origW: number; origH: number } | null = null
let lastClick: { uid: string | null; t: number } = { uid: null, t: 0 }

const BOARD_BG = '#1b1b18'
const DOT_COLOR = '#33322b'
const ACCENT = '#df5430'
/** 选中框 / 手柄颜色，与像素风主题一致 */
const HANDLE_CELLS = 3

const cursorStyle = computed(() => {
  if (!hover.value) return {}
  return {
    left: `${hover.value.x * cellPx.value}px`,
    top: `${hover.value.y * cellPx.value}px`,
    width: `${cellPx.value}px`,
    height: `${cellPx.value}px`,
  }
})

/** 悬停光标：放置组件上变为移动/缩放提示 */
const hoverCursor = ref('crosshair')

function readCellSize(): number {
  const wrap = wrapEl.value
  if (!wrap) return 8
  const maxWidth = wrap.clientWidth - 24
  const maxHeight = wrap.clientHeight - 24
  if (!maxWidth || !maxHeight) return 8
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

  const cellsArr = store.cells
  for (let y = 0; y < h; y++) {
    for (let x = 0; x < w; x++) {
      drawCell(x, y, cellsArr[y * w + x], false)
    }
  }

  // 矩形预览：拖拽过程中的描边
  if (rectPreview.value) {
    fillPreview(rectPreview.value)
  }
  if (linePreview.value) {
    fillPreview(linePreview.value)
  }

  // 放置组件：点阵 + 边框 + 缩放手柄
  drawPlaced()
}

function drawPlaced() {
  if (!ctx) return
  const gap = cell >= 5 ? 1 : 0
  const ink = store.darkestColor.hex
  for (const pc of store.placedComponents) {
    const pattern = placedPattern(pc)
    ctx.fillStyle = ink
    for (let y = 0; y < pc.h; y++) {
      for (let x = 0; x < pc.w; x++) {
        if (pattern[y * pc.w + x] !== '#') continue
        ctx.fillRect((pc.x + x) * cell + gap, (pc.y + y) * cell + gap, cell - gap * 2, cell - gap * 2)
      }
    }
    const selected = store.selectedPlacedUid === pc.uid
    ctx.setLineDash([4, 3])
    ctx.strokeStyle = selected ? ACCENT : 'rgba(223, 84, 48, 0.5)'
    ctx.strokeRect(pc.x * cell + 0.5, pc.y * cell + 0.5, pc.w * cell - 1, pc.h * cell - 1)
    ctx.setLineDash([])
    if (selected) {
      // 右下角缩放手柄
      const hs = HANDLE_CELLS * cell
      ctx.fillStyle = ACCENT
      ctx.fillRect((pc.x + pc.w) * cell - hs, (pc.y + pc.h) * cell - hs, hs, hs)
    }
  }
}

function hitPlaced(point: { x: number; y: number }) {
  const list = store.placedComponents
  for (let i = list.length - 1; i >= 0; i--) {
    const pc = list[i]
    if (point.x < pc.x || point.x >= pc.x + pc.w || point.y < pc.y || point.y >= pc.y + pc.h) continue
    const onHandle =
      point.x >= pc.x + pc.w - HANDLE_CELLS && point.y >= pc.y + pc.h - HANDLE_CELLS
    return { pc, onHandle }
  }
  return null
}

/** 单格绘制；增量调用时先清底避免旧点/棋盘点残留，全量渲染时已清屏可跳过 */
function drawCell(x: number, y: number, color?: string, clear = true) {
  if (!ctx) return
  const w = store.width
  const c = color ?? store.cells[y * w + x]
  const gap = cell >= 5 ? 1 : 0
  const isRound = store.shape === 'round'
  const radius = Math.max(1, Math.round(cell * 0.14))
  const dotSize = Math.max(1, Math.min(2, Math.round(cell * 0.14)))
  const px = x * cell
  const py = y * cell

  if (clear) {
    ctx.fillStyle = BOARD_BG
    ctx.fillRect(px, py, cell, cell)
  }

  if (c) {
    ctx.fillStyle = c
    if (isRound) {
      ctx.beginPath()
      ctx.arc(px + gap + (cell - gap * 2) / 2, py + gap + (cell - gap * 2) / 2, (cell - gap * 2) / 2, 0, Math.PI * 2)
      ctx.fill()
    } else if (radius > 1) {
      roundRectPath(ctx, px + gap, py + gap, cell - gap * 2, radius)
      ctx.fill()
    } else {
      ctx.fillRect(px + gap, py + gap, cell - gap * 2, cell - gap * 2)
    }
  } else if (cell >= 6) {
    ctx.fillStyle = DOT_COLOR
    const offset = (cell - dotSize) / 2
    ctx.fillRect(px + offset, py + offset, dotSize, dotSize)
  }
}

/** 矩形预览描边叠加 */
function fillPreview(points: { x: number; y: number }[]) {
  if (!ctx) return
  ctx.fillStyle = store.color
  const gap = cell >= 5 ? 1 : 0
  const psize = cell - gap * 2
  for (const p of points) {
    ctx.fillRect(p.x * cell + gap, p.y * cell + gap, psize, psize)
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
  const idx = y * store.width + x
  if (store.cells[idx] === color) return
  store.setCell(x, y, color)
  // 增量绘制：只画变了的这一格
  drawCell(x, y)
}

/** 矩形像素：填充=实心；否则描边（粗细 thickness，格子太小时退化为实心） */
function rectCells(a: { x: number; y: number }, b: { x: number; y: number }) {
  const pts: { x: number; y: number }[] = []
  const x1 = Math.min(a.x, b.x)
  const x2 = Math.max(a.x, b.x)
  const y1 = Math.min(a.y, b.y)
  const y2 = Math.max(a.y, b.y)
  const t = Math.max(1, Math.min(6, store.rectThickness))

  if (store.rectFilled) {
    for (let y = y1; y <= y2; y++) {
      for (let x = x1; x <= x2; x++) pts.push({ x, y })
    }
    return pts
  }
  for (let y = y1; y <= y2; y++) {
    for (let x = x1; x <= x2; x++) {
      if (x - x1 < t || x2 - x < t || y - y1 < t || y2 - y < t) pts.push({ x, y })
    }
  }
  return pts
}

/** Bresenham 直线像素（粗细 thickness，按方块加粗） */
function lineCells(a: { x: number; y: number }, b: { x: number; y: number }) {
  const pts: { x: number; y: number }[] = []
  const t = Math.max(1, Math.min(6, store.rectThickness))
  let { x: x0, y: y0 } = a
  const x1 = b.x
  const y1 = b.y
  const dx = Math.abs(x1 - x0)
  const dy = Math.abs(y1 - y0)
  const sx = x0 < x1 ? 1 : -1
  const sy = y0 < y1 ? 1 : -1
  let err = dx - dy
  const seen = new Set<number>()
  for (;;) {
    for (let oy = 0; oy < t; oy++) {
      for (let ox = 0; ox < t; ox++) {
        const key = (y0 + oy) * store.width + (x0 + ox)
        if (!seen.has(key)) {
          seen.add(key)
          pts.push({ x: x0 + ox, y: y0 + oy })
        }
      }
    }
    if (x0 === x1 && y0 === y1) break
    const e2 = 2 * err
    if (e2 > -dy) {
      err -= dy
      x0 += sx
    }
    if (e2 < dx) {
      err += dx
      y0 += sy
    }
  }
  return pts
}

/** 预览增量更新：只重画退出预览的格子，其余叠加预览色 */
function updatePreview(next: { x: number; y: number }[], state: typeof rectPreview) {
  const prev = state.value ?? []
  if (next.length === prev.length && next.every((p, i) => prev[i] === p)) return
  const nextKeys = new Set(next.map((p) => p.y * store.width + p.x))
  for (const p of prev) {
    if (!nextKeys.has(p.y * store.width + p.x)) drawCell(p.x, p.y)
  }
  fillPreview(next)
  state.value = next
}

/** 拖拽结束：把预览像素提交到画布 */
function commitPreview(start: typeof rectStart, state: typeof rectPreview) {
  const points = state.value
  start.value = null
  state.value = null
  if (!points) return
  const color = store.color
  for (const p of points) store.setCell(p.x, p.y, color)
}

function onPointerDown(event: PointerEvent) {
  if (event.pointerType === 'mouse' && event.button !== 0) return
  const point = pickCell(event)
  if (!point) return

  canvasEl.value?.setPointerCapture(event.pointerId)
  hover.value = point
  store.cursor = point

  // 放置组件优先：命中则选中并进入移动/缩放；双击落定为像素
  const hit = hitPlaced(point)
  const now = performance.now()
  if (hit) {
    store.selectPlaced(hit.pc.uid)
    if (lastClick.uid === hit.pc.uid && now - lastClick.t < 350) {
      commitPlaced(hit.pc)
      lastClick = { uid: null, t: 0 }
      return
    }
    lastClick = { uid: hit.pc.uid, t: now }
    if (hit.onHandle) {
      resizeState = { uid: hit.pc.uid, startPX: point.x, startPY: point.y, origW: hit.pc.w, origH: hit.pc.h }
    } else {
      moveState = { uid: hit.pc.uid, startPX: point.x, startPY: point.y, origX: hit.pc.x, origY: hit.pc.y }
    }
    return
  }
  lastClick = { uid: null, t: 0 }

  store.pushUndo()

  if (store.tool === 'fill') {
    store.floodFill(point.x, point.y, store.color)
    return
  }

  if (store.tool === 'rect') {
    rectStart.value = point
    rectPreview.value = [point]
    painting = true
    fillPreview(rectPreview.value)
    return
  }

  if (store.tool === 'line') {
    lineStart.value = point
    linePreview.value = [point]
    painting = true
    fillPreview(linePreview.value)
    return
  }

  painting = true
  last = point
  applyBrush(point.x, point.y)
}

/** pointermove 只记录最新点，实际绘制统一在 rAF 帧里做：
 *  高频事件不再逐个同步处理，快速拖拽时每帧工作量有上限 */
let pendingPoint: { x: number; y: number } | null = null
let moveFrame = 0

function processMove() {
  moveFrame = 0
  const point = pendingPoint
  pendingPoint = null
  if (!point) return

  hover.value = point
  store.cursor = point

  // 放置组件：拖动 / 拖角缩放
  if (moveState) {
    const pc = store.placedComponents.find((p) => p.uid === moveState!.uid)
    if (pc) {
      const nx = Math.max(0, Math.min(moveState.origX + point.x - moveState.startPX, store.width - pc.w))
      const ny = Math.max(0, Math.min(moveState.origY + point.y - moveState.startPY, store.height - pc.h))
      store.updatePlaced(pc.uid, { x: nx, y: ny })
    }
    return
  }
  if (resizeState) {
    const pc = store.placedComponents.find((p) => p.uid === resizeState!.uid)
    if (pc) {
      const nw = Math.max(4, Math.min(resizeState.origW + point.x - resizeState.startPX, store.width - pc.x))
      const nh = Math.max(2, Math.min(resizeState.origH + point.y - resizeState.startPY, store.height - pc.y))
      store.updatePlaced(pc.uid, { w: nw, h: nh })
    }
    return
  }

  // 悬停光标反馈
  if (!painting) {
    const hit = hitPlaced(point)
    hoverCursor.value = hit ? (hit.onHandle ? 'nwse-resize' : 'move') : 'crosshair'
    return
  }

  if (store.tool === 'fill') return

  // 矩形拖拽：增量更新预览
  if (rectStart.value) {
    updatePreview(rectCells(rectStart.value, point), rectPreview)
    return
  }

  // 直线拖拽：同矩形增量策略
  if (lineStart.value) {
    updatePreview(lineCells(lineStart.value, point), linePreview)
    return
  }

  const from = last ?? point
  const dx = point.x - from.x
  const dy = point.y - from.y
  const steps = Math.max(Math.abs(dx), Math.abs(dy))
  for (let s = 1; s <= steps; s++) {
    applyBrush(from.x + Math.round((dx * s) / steps), from.y + Math.round((dy * s) / steps))
  }
  last = point
}

function flushMove() {
  if (moveFrame) {
    cancelAnimationFrame(moveFrame)
    moveFrame = 0
  }
  processMove()
}

function onPointerMove(event: PointerEvent) {
  const point = pickCell(event)
  if (!point) return
  pendingPoint = point
  if (!moveFrame) moveFrame = requestAnimationFrame(processMove)
}

function onPointerUp(event: PointerEvent) {
  // 先处理完未落帧的移动，保证笔画/拖拽完整
  flushMove()

  moveState = null
  resizeState = null

  // 矩形结束：提交像素
  if (rectStart.value && rectPreview.value) {
    commitPreview(rectStart, rectPreview)
    scheduleRender()
  }

  // 直线结束：提交像素
  if (lineStart.value && linePreview.value) {
    commitPreview(lineStart, linePreview)
    scheduleRender()
  }

  painting = false
  last = null
  const canvas = canvasEl.value
  if (canvas?.hasPointerCapture(event.pointerId)) {
    canvas.releasePointerCapture(event.pointerId)
  }
}

function onPointerLeave() {
  if (painting) return
  if (moveFrame) {
    cancelAnimationFrame(moveFrame)
    moveFrame = 0
    pendingPoint = null
  }
  hover.value = null
  store.cursor = null
}

/* ------------------------------------------------- 组件库拖入放置 */

function onDragOver(event: DragEvent) {
  event.preventDefault()
  if (event.dataTransfer) event.dataTransfer.dropEffect = 'copy'
}

function onDrop(event: DragEvent) {
  event.preventDefault()
  const id = event.dataTransfer?.getData('lib-comp')
  if (!id) return
  const comp = getLibComponent(id)
  if (!comp) return
  const canvas = canvasEl.value
  if (!canvas) return
  const rect = canvas.getBoundingClientRect()
  void placeComponent(comp, (event.clientX - rect.left) / cell, (event.clientY - rect.top) / cell)
}

function handleWindowResize() {
  layout()
}

/** 键盘：Delete 删除选中放置组件，Enter 落定，Esc 取消选中 */
function onKeyDown(e: KeyboardEvent) {
  const t = e.target as HTMLElement | null
  if (t && (t.tagName === 'INPUT' || t.tagName === 'TEXTAREA' || t.tagName === 'SELECT')) return
  const uid = store.selectedPlacedUid
  if (!uid) return
  const pc = store.placedComponents.find((p) => p.uid === uid)
  if (!pc) return
  if (e.key === 'Delete' || e.key === 'Backspace') {
    store.removePlaced(uid)
  } else if (e.key === 'Enter') {
    commitPlaced(pc)
  } else if (e.key === 'Escape') {
    store.selectPlaced(null)
  } else {
    return
  }
  e.preventDefault()
}

watch(
  () => [store.width, store.height, store.shape],
  () => layout(),
  { flush: 'post' },
)

/** rAF 合并渲染：revision 短时间多次变化（撤销/填充/组件烙印等）只绘一帧 */
let renderQueued = false
function scheduleRender() {
  if (renderQueued) return
  renderQueued = true
  requestAnimationFrame(() => {
    renderQueued = false
    render()
  })
}

watch(
  () => store.revision,
  () => {
    // 手绘/矩形/直线拖拽过程中已按格增量绘制，跳过全量重绘
    if (painting || rectStart.value || lineStart.value) return
    scheduleRender()
  },
  { flush: 'post' },
)

// 放置组件列表 / 选中态变化 → 重绘叠加层
watch(() => store.placedComponents, () => scheduleRender(), { deep: true })
watch(() => store.selectedPlacedUid, () => scheduleRender())

// 切换工具时取消未完成的矩形/直线
watch(
  () => store.tool,
  () => {
    rectStart.value = null
    rectPreview.value = null
    lineStart.value = null
    linePreview.value = null
    scheduleRender()
  },
)

onMounted(() => {
  layout()
  window.addEventListener('keydown', onKeyDown)
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
  if (moveFrame) {
    cancelAnimationFrame(moveFrame)
    moveFrame = 0
  }
  window.removeEventListener('keydown', onKeyDown)
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
        :style="{ cursor: hoverCursor }"
        @pointerdown="onPointerDown"
        @pointermove="onPointerMove"
        @pointerup="onPointerUp"
        @pointercancel="onPointerUp"
        @pointerleave="onPointerLeave"
        @dragover="onDragOver"
        @drop="onDrop"
        @contextmenu.prevent
      />
      <span v-if="hover" class="board__cursor" :style="cursorStyle" />
    </div>
  </div>
</template>

<style scoped>
.board {
  flex: 1;
  min-height: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 12px;
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

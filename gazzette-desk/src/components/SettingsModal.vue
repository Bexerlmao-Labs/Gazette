<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import AppModal from '@/components/AppModal.vue'
import PixelIcon from '@/components/PixelIcon.vue'
import { SCREEN_PRESETS, useDisplayStore } from '@/stores/display'

const store = useDisplayStore()

/** 白色为底色，始终启用、不可取消勾选 */
const LOCKED_COLORS = 1

const duplicateChars = computed(() => {
  const seen = new Map<string, number>()
  for (const c of store.palette) {
    const key = c.char.trim().toUpperCase()
    if (!key) continue
    seen.set(key, (seen.get(key) ?? 0) + 1)
  }
  return [...seen.entries()].filter(([, n]) => n > 1).map(([k]) => k)
})

function onCharInput(index: number, value: string) {
  const item = store.palette[index]
  if (!item) return
  item.char = value.slice(-1).toUpperCase()
}

/* ---------------- 屏幕尺寸（默认隐藏，从蓝牙自动读取；此处保留手动入口） ---------------- */
const sizeOpen = ref(false)
const wInput = ref(store.width)
const hInput = ref(store.height)

watch(
  () => [store.width, store.height],
  () => {
    wInput.value = store.width
    hInput.value = store.height
  },
)

function applySize() {
  store.resize(Number(wInput.value), Number(hInput.value))
  wInput.value = store.width
  hInput.value = store.height
}

function applyPreset(preset: (typeof SCREEN_PRESETS)[number]) {
  store.resize(preset.w, preset.h)
}
</script>

<template>
  <AppModal title="墨水屏设置" subtitle="E-PAPER INK SETTINGS" @close="store.closeModal()">
    <div class="field">
      <div class="field__head">
        <span class="label">墨水颜色与填充符</span>
        <span class="count">已启用 {{ store.activePalette.length }} / {{ store.palette.length }}</span>
      </div>

      <div class="colors">
        <div
          v-for="(c, i) in store.palette"
          :key="c.hex"
          class="color-row"
          :class="{ 'color-row--off': !c.enabled }"
        >
          <label
            class="check"
            :class="{ 'check--locked': i < LOCKED_COLORS }"
            :title="i < LOCKED_COLORS ? '底色，始终启用' : '勾选后可在画板中使用该颜色'"
          >
            <input v-model="c.enabled" type="checkbox" :disabled="i < LOCKED_COLORS" />
          </label>
          <span class="color-swatch" :style="{ background: c.hex }" :title="`${c.name} ${c.hex}`" />
          <input
            class="input color-char"
            :value="c.char"
            placeholder="0"
            maxlength="1"
            :disabled="!c.enabled"
            title="导出与推送时该颜色使用的位图填充符"
            @input="onCharInput(i, ($event.target as HTMLInputElement).value)"
          />
        </div>
      </div>

      <div class="color-actions">
        <p v-if="duplicateChars.length" class="dup">
          填充符重复：{{ duplicateChars.join(' ') }}，请修改为不同字符
        </p>
      </div>
    </div>

    <div class="divider" />

    <div class="size-entry">
      <button class="size-toggle" type="button" @click="sizeOpen = !sizeOpen">
        <PixelIcon name="screen" :size="14" />
        <span>屏幕尺寸</span>
        <code>{{ store.width }} × {{ store.height }}</code>
        <PixelIcon :name="sizeOpen ? 'close' : 'spark'" :size="12" />
      </button>
      <p v-if="!sizeOpen" class="hint">已连接蓝牙时尺寸自动从设备读取，一般无需手动调整</p>

      <div v-if="sizeOpen" class="size-body">
        <span class="size-body__label">固定尺寸预设</span>
        <div class="presets">
          <button
            v-for="preset in SCREEN_PRESETS"
            :key="preset.label"
            class="chip"
            :class="{ active: store.width === preset.w && store.height === preset.h }"
            @click="applyPreset(preset)"
          >
            {{ preset.label }}
          </button>
        </div>

        <span class="size-body__label">自定义尺寸</span>
        <div class="size">
          <div class="size__box">
            <span class="size__key">W</span>
            <input
              v-model.number="wInput"
              class="input size__input"
              type="number"
              min="4"
              max="1024"
              aria-label="屏幕宽度"
              @change="applySize"
              @keyup.enter="applySize"
            />
          </div>
          <span class="size__x">×</span>
          <div class="size__box">
            <span class="size__key">H</span>
            <input
              v-model.number="hInput"
              class="input size__input"
              type="number"
              min="4"
              max="1024"
              aria-label="屏幕高度"
              @change="applySize"
              @keyup.enter="applySize"
            />
          </div>
        </div>
        <p class="hint">范围 4 – 1024 px，调整后保留左上角画面</p>
      </div>
    </div>
  </AppModal>
</template>

<style scoped>
.field {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.field__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.count {
  color: var(--muted);
  font-size: 9.5px;
  letter-spacing: 0.1em;
}

.hint {
  color: var(--muted);
  font-size: 9.5px;
  letter-spacing: 0.04em;
}

.colors {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.color-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 9px;
  border: 1px solid var(--line);
  background: var(--paper);
}

.color-row--off {
  opacity: 0.45;
}

.check {
  display: flex;
  align-items: center;
  cursor: pointer;
}

.check--locked {
  cursor: not-allowed;
}

.check input {
  width: 14px;
  height: 14px;
  accent-color: var(--ink);
  cursor: inherit;
}

.color-swatch {
  flex: 1;
  height: 26px;
  border: 2px solid var(--ink);
}

.color-char {
  flex: none;
  width: 44px;
  height: 26px;
  padding: 0 4px;
  text-align: center;
  text-transform: uppercase;
}

.color-swatch {
  position: relative;
  width: 34px;
  height: 34px;
  border: 2px solid var(--ink);
  cursor: pointer;
  overflow: hidden;
}

.color-swatch input {
  position: absolute;
  inset: 0;
  opacity: 0;
  cursor: pointer;
}

.color-name {
  height: 34px;
}

.color-char {
  height: 26px;
  text-align: center;
  text-transform: uppercase;
}

.color-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.dup {
  color: var(--accent);
  font-size: 9.5px;
  letter-spacing: 0.04em;
}

.divider {
  height: 1px;
  background: var(--line);
  margin: 18px 0;
}

.size-entry {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.size-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 4px;
}

.size-body__label {
  margin-top: 4px;
  color: var(--muted);
  font-size: 9px;
  font-weight: 700;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.size-toggle {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 34px;
  padding: 0 10px;
  border: 2px solid var(--ink);
  background: var(--paper);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.08em;
  cursor: pointer;
}

.size-toggle:hover {
  background: #fffdf6;
}

.size-toggle code {
  margin-left: auto;
  color: var(--muted);
  font-size: 10px;
}

.presets {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}

.chip {
  height: 32px;
  border: 2px solid var(--ink);
  background: var(--paper);
  color: var(--ink-2);
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.08em;
  cursor: pointer;
}

.chip.active {
  background: var(--accent);
  color: #fff8f1;
}

.size {
  display: flex;
  align-items: center;
  gap: 8px;
}

.size__box {
  position: relative;
  flex: 1;
}

.size__key {
  position: absolute;
  top: 50%;
  left: 8px;
  transform: translateY(-50%);
  color: var(--muted);
  font-size: 10px;
  font-weight: 700;
  pointer-events: none;
}

.size__input {
  padding-left: 22px;
}

.size__x {
  color: var(--muted);
  font-size: 12px;
}
</style>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import PanelCard from '@/components/PanelCard.vue'
import PixelIcon from '@/components/PixelIcon.vue'
import {
  createDevice,
  deleteDevice,
  deleteScene,
  fetchDeviceDisplay,
  listDevices,
  listScenes,
  previewScene,
  saveScene,
  updateDevice,
} from '@/utils/cloud-api'
import { DEVICE_STATUS, type BitmapEncoding, type CloudDevice, type CloudScene } from '@/types/cloud'

/* ------------------------------------------------------------- 状态 */

const scenes = ref<CloudScene[]>([])
const devices = ref<CloudDevice[]>([])
const loading = ref(false)
const error = ref('')

const defaultEncoding = (): BitmapEncoding => ({
  bitOrder: 'msb',
  black: '1',
  white: '0',
  red: '',
  yellow: '',
  blue: '',
  green: '',
})

/** 可选颜色定义：黑/白固定支持，其余四色可勾选启用 */
const colorDefs = [
  { key: 'black', label: '黑', fixed: true },
  { key: 'white', label: '白', fixed: true },
  { key: 'red', label: '红', fixed: false },
  { key: 'yellow', label: '黄', fixed: false },
  { key: 'blue', label: '蓝', fixed: false },
  { key: 'green', label: '绿', fixed: false },
] as const
type ColorKey = (typeof colorDefs)[number]['key']

/** 启用颜色时的默认编码 */
const COLOR_DEFAULT_CODES: Record<ColorKey, string> = {
  black: '1',
  white: '0',
  red: '2',
  yellow: '3',
  blue: '4',
  green: '5',
}

function toggleColor(key: ColorKey, ev: Event) {
  const enc = sceneForm.value.bitmapEncoding
  enc[key] = (ev.target as HTMLInputElement).checked ? COLOR_DEFAULT_CODES[key] : ''
}

const sceneForm = ref({
  name: '',
  width: 240,
  height: 416,
  baseImageBase64: '',
  overlayDsl: '',
  bitmapEncoding: defaultEncoding(),
})
const deviceForm = ref({ name: '', model: '', width: 240, height: 416 })

/** 编辑中的场景 id → DSL 文本 */
const editingDsl = ref<Record<number, string>>({})
/** 场景 id → 预览图 blob 地址 */
const previewUrls = ref<Record<number, string>>({})
/** 设备 id → 拉取自测结果 */
const probeResult = ref<Record<number, string>>({})

const sceneById = computed(() => new Map(scenes.value.map((s) => [s.id, s])))

/* ------------------------------------------------------------- 数据加载 */

async function loadAll() {
  loading.value = true
  error.value = ''
  try {
    const [s, d] = await Promise.all([listScenes(), listDevices()])
    scenes.value = s
    devices.value = d
    // 为每个场景初始化 DSL 编辑副本
    for (const sc of s) {
      if (editingDsl.value[sc.id] === undefined) {
        editingDsl.value[sc.id] = sc.overlayDsl ?? ''
      }
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

onMounted(loadAll)

/* ------------------------------------------------------------- 场景操作 */

function onBaseImageChange(ev: Event) {
  const file = (ev.target as HTMLInputElement).files?.[0]
  if (!file) return
  const reader = new FileReader()
  reader.onload = () => {
    sceneForm.value.baseImageBase64 = reader.result as string
  }
  reader.readAsDataURL(file)
}

async function submitScene() {
  if (!sceneForm.value.name.trim()) return
  loading.value = true
  error.value = ''
  try {
    await saveScene({
      name: sceneForm.value.name,
      width: sceneForm.value.width,
      height: sceneForm.value.height,
      baseImageBase64: sceneForm.value.baseImageBase64 || undefined,
      overlayDsl: sceneForm.value.overlayDsl || undefined,
      bitmapEncoding: { ...sceneForm.value.bitmapEncoding },
    })
    sceneForm.value = {
      name: '',
      width: 240,
      height: 416,
      baseImageBase64: '',
      overlayDsl: '',
      bitmapEncoding: defaultEncoding(),
    }
    await loadAll()
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

async function saveDsl(scene: CloudScene) {
  loading.value = true
  error.value = ''
  try {
    await saveScene({
      id: scene.id,
      name: scene.name,
      width: scene.width,
      height: scene.height,
      overlayDsl: editingDsl.value[scene.id] ?? scene.overlayDsl ?? '',
      bitmapEncoding: scene.bitmapEncoding ?? defaultEncoding(),
    })
    await loadAll()
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

async function removeScene(id: number) {
  loading.value = true
  error.value = ''
  try {
    await deleteScene(id)
    await loadAll()
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

async function preview(id: number) {
  try {
    // v1 数据集为空，datetime 等自渲染元素即可体现动态效果
    previewUrls.value[id] = await previewScene(id)
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  }
}

/* ------------------------------------------------------------- 设备操作 */

async function submitDevice() {
  if (!deviceForm.value.name.trim()) return
  loading.value = true
  error.value = ''
  try {
    await createDevice({
      name: deviceForm.value.name,
      model: deviceForm.value.model || undefined,
      width: deviceForm.value.width,
      height: deviceForm.value.height,
    })
    deviceForm.value = { name: '', model: '', width: 240, height: 416 }
    await loadAll()
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

async function bindScene(device: CloudDevice, ev: Event) {
  const value = (ev.target as HTMLSelectElement).value
  loading.value = true
  error.value = ''
  try {
    await updateDevice(device.id, {
      bindScene: true,
      currentSceneId: value === '' ? null : Number(value),
    })
    await loadAll()
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

async function removeDevice(id: number) {
  loading.value = true
  error.value = ''
  try {
    await deleteDevice(id)
    await loadAll()
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

/** 用固件参数直接调拉取接口自测 */
async function probe(device: CloudDevice) {
  try {
    const resp = await fetchDeviceDisplay(device.deviceSn, device.deviceKey, device.currentHash ?? '')
    probeResult.value[device.id] = resp.changed
      ? `已下发新画面 hash=${resp.hash?.slice(0, 8)}…`
      : '无更新（哈希一致）'
  } catch (e) {
    probeResult.value[device.id] = e instanceof Error ? e.message : String(e)
  }
}

function fmtTime(t?: string) {
  return t ? t.replace('T', ' ').slice(0, 19) : '—'
}
</script>

<template>
  <div class="cloud-view">
    <div v-if="error" class="error-bar">{{ error }}</div>

    <!-- 场景：原始画面 + 动态 DSL -->
    <PanelCard num="01" title="画面场景" subtitle="原始画面 + 动态图层 DSL">
      <template #action>
        <span class="hint">{{ scenes.length }} 个场景</span>
      </template>

      <div v-for="s in scenes" :key="s.id" class="scene-item">
        <div class="scene-item__head">
          <strong>{{ s.name }}</strong>
          <span class="hint mono">{{ s.width }}×{{ s.height }} · {{ fmtTime(s.updatedAt) }}</span>
        </div>

        <label class="label">动态图层 DSL</label>
        <textarea
          v-model="editingDsl[s.id]"
          class="input dsl-editor"
          spellcheck="false"
          placeholder="datetime 8,4,140,24 format=%H:%M font=3"
        />
        <div class="scene-item__actions">
          <button class="btn btn--ghost btn--sm" @click="saveDsl(s)">
            <PixelIcon name="send" :size="13" />
            保存 DSL
          </button>
          <button class="btn btn--ghost btn--sm" @click="preview(s.id)">
            <PixelIcon name="eye" :size="13" />
            渲染预览
          </button>
          <button class="btn btn--ghost btn--sm" @click="removeScene(s.id)">
            <PixelIcon name="trash" :size="13" />
            删除
          </button>
        </div>
        <img
          v-if="previewUrls[s.id]"
          class="preview"
          :src="previewUrls[s.id]"
          :alt="`场景 ${s.name} 预览`"
        />
      </div>

      <div class="divider" />

      <label class="label">新建场景</label>
      <div class="field-row">
        <div class="field grow">
          <span class="label">名称</span>
          <input v-model="sceneForm.name" class="input" placeholder="书房天气面板" />
        </div>
        <div class="field">
          <span class="label">宽</span>
          <input v-model.number="sceneForm.width" class="input" type="number" />
        </div>
        <div class="field">
          <span class="label">高</span>
          <input v-model.number="sceneForm.height" class="input" type="number" />
        </div>
      </div>
      <div class="field">
        <span class="label">原始画面（PNG 底图，可空）</span>
        <input class="input" type="file" accept="image/png,image/jpeg" @change="onBaseImageChange" />
      </div>
      <div class="field">
        <span class="label">动态图层 DSL（可空）</span>
        <textarea
          v-model="sceneForm.overlayDsl"
          class="input dsl-editor"
          spellcheck="false"
          placeholder="datetime 8,4,140,24 format=%H:%M font=3&#10;text 8,34,200,16 font=1 &quot;{weather.desc}&quot;"
        />
      </div>
      <div class="field">
        <span class="label">位图定义方案（转换模板，适配目标单片机/驱动库）</span>
        <div class="field-row">
          <div class="field">
            <span class="label">位序</span>
            <select v-model="sceneForm.bitmapEncoding.bitOrder" class="input">
              <option value="msb">MSB</option>
              <option value="lsb">LSB</option>
            </select>
          </div>
          <div class="field">
            <span class="label">位宽（每像素）</span>
            <select v-model.number="sceneForm.bitmapEncoding.bits" class="input">
              <option :value="undefined">自动</option>
              <option :value="1">1</option>
              <option :value="2">2</option>
              <option :value="3">3</option>
              <option :value="4">4</option>
            </select>
          </div>
        </div>
        <div class="field-row">
          <div v-for="c in colorDefs" :key="c.key" class="field">
            <span class="label">
              {{ c.label }}
              <input
                v-if="!c.fixed"
                type="checkbox"
                :checked="sceneForm.bitmapEncoding[c.key] !== ''"
                @change="toggleColor(c.key, $event)"
              />
            </span>
            <input
              v-model="sceneForm.bitmapEncoding[c.key]"
              class="input"
              maxlength="1"
              placeholder="0-F"
              :disabled="!c.fixed && sceneForm.bitmapEncoding[c.key] === ''"
            />
          </div>
        </div>
        <span class="hint">编码为 16 进制单字符；未勾选的颜色不支持，其像素回退为黑白灰度</span>
      </div>
      <button class="btn btn--primary" :disabled="loading" @click="submitScene">
        <PixelIcon name="upload" :size="14" />
        创建场景
      </button>
    </PanelCard>

    <!-- 设备 -->
    <PanelCard num="02" title="墨水屏设备" subtitle="SN + KEY 鉴权 · 绑定场景 · 轮询拉取">
      <template #action>
        <span class="hint">{{ devices.length }} 台设备</span>
      </template>

      <div v-for="d in devices" :key="d.id" class="device-item">
        <div class="device-item__head">
          <strong>{{ d.name }}</strong>
          <span class="status-chip" :class="`is-${d.status}`">
            {{ DEVICE_STATUS[d.status]?.label ?? '未知' }}
          </span>
        </div>
        <div class="device-meta mono">
          <div>SN：{{ d.deviceSn }}</div>
          <div>KEY：{{ d.deviceKey }}</div>
          <div>最近在线：{{ fmtTime(d.lastOnlineAt) }}<template v-if="d.battery != null"> · 电量 {{ d.battery }}%</template></div>
          <div v-if="d.currentHash">画面哈希：{{ d.currentHash.slice(0, 16) }}…</div>
        </div>
        <div class="field-row">
          <div class="field grow">
            <span class="label">绑定场景</span>
            <select
              class="input"
              :value="d.currentSceneId ?? ''"
              @change="bindScene(d, $event)"
            >
              <option value="">（未绑定）</option>
              <option v-for="s in scenes" :key="s.id" :value="s.id">{{ s.name }}</option>
            </select>
          </div>
          <button class="btn btn--ghost btn--sm" @click="probe(d)">
            <PixelIcon name="plug" :size="13" />
            拉取自测
          </button>
          <button class="btn btn--ghost btn--sm" @click="removeDevice(d.id)">
            <PixelIcon name="trash" :size="13" />
            删除
          </button>
        </div>
        <div v-if="probeResult[d.id]" class="hint">{{ probeResult[d.id] }}</div>
      </div>

      <div class="divider" />

      <label class="label">添加设备</label>
      <div class="field-row">
        <div class="field grow">
          <span class="label">名称</span>
          <input v-model="deviceForm.name" class="input" placeholder="书房日历屏" />
        </div>
        <div class="field grow">
          <span class="label">型号</span>
          <input v-model="deviceForm.model" class="input" placeholder="ESP32-DEPG0213" />
        </div>
        <div class="field">
          <span class="label">宽</span>
          <input v-model.number="deviceForm.width" class="input" type="number" />
        </div>
        <div class="field">
          <span class="label">高</span>
          <input v-model.number="deviceForm.height" class="input" type="number" />
        </div>
      </div>
      <button class="btn btn--primary" :disabled="loading" @click="submitDevice">
        <PixelIcon name="screen" :size="14" />
        添加设备
      </button>
    </PanelCard>
  </div>
</template>

<style scoped>
.cloud-view {
  width: 100%;
  max-width: 640px;
  margin: 0 auto;
  padding: 22px 24px 30px;
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.error-bar {
  padding: 10px 14px;
  border: 2px solid var(--ink);
  background: var(--accent-soft);
  color: var(--ink);
  font-size: 11px;
}

.scene-item,
.device-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
  border: 2px solid var(--ink);
  background: var(--paper);
}

.scene-item__head,
.device-item__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.scene-item__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.device-item {
  gap: 6px;
}

.device-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 10px;
  color: var(--ink-2);
  word-break: break-all;
}

.field-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: flex-end;
}

.field-row .field {
  margin: 0;
}

.grow {
  flex: 1;
  min-width: 120px;
}

.dsl-editor {
  min-height: 88px;
  font-family: var(--font-mono);
  font-size: 11px;
  line-height: 1.5;
  resize: vertical;
}

.preview {
  align-self: center;
  max-width: 200px;
  border: 2px solid var(--ink);
  image-rendering: pixelated;
  box-shadow: var(--shadow-sm);
}

.status-chip {
  flex: none;
  padding: 3px 8px;
  border: 2px solid var(--ink);
  font-size: 9px;
  letter-spacing: 0.08em;
  background: var(--paper-3);
}

.status-chip.is-1 {
  background: var(--accent-2);
}

.status-chip.is-2 {
  color: var(--muted);
}

.divider {
  height: 2px;
  background: var(--line);
}

@media (max-width: 900px) {
  .cloud-view {
    padding: 16px 14px 24px;
  }
}
</style>

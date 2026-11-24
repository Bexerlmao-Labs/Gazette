import { computed, reactive, ref, shallowRef, watch } from 'vue'
import { defineStore } from 'pinia'
import { registerDevice } from '@/utils/cloud-api'
import { flattenPlaced, type PlacedComponent } from '@/components/library/library'
import { onFontLoaded } from '@/components/library/font'

export type Tool = 'pen' | 'eraser' | 'fill' | 'rect' | 'line'
export type PixelShape = 'square' | 'round'
export type ModalName = 'wifi' | 'export' | 'settings' | 'datasource' | null

export interface InkColor {
  hex: string
  name: string
  char: string
  /** 是否启用（是否出现在工具栏色板中） */
  enabled: boolean
}

/**
 * 墨水屏固定六色：白 / 黑 / 红 / 黄 / 蓝 / 绿。
 * 颜色本身不可增删改，只能在「墨水屏设置」中勾选启用并自定义位图填充符；
 * 白色为底色，始终启用；其余颜色默认启用黑/红，可在设置中勾选。
 */
export const DEFAULT_INK_COLORS: InkColor[] = [
  { hex: '#ffffff', name: '白', char: '0', enabled: true },
  { hex: '#1c1b18', name: '黑', char: '1', enabled: true },
  { hex: '#e4574c', name: '红', char: '2', enabled: true },
  { hex: '#f2c53d', name: '黄', char: '3', enabled: false },
  { hex: '#2e6fdf', name: '蓝', char: '4', enabled: false },
  { hex: '#35a35a', name: '绿', char: '5', enabled: false },
]

export const SCREEN_PRESETS = [
  { label: '8×8', w: 8, h: 8 },
  { label: '16×16', w: 16, h: 16 },
  { label: '32×8', w: 32, h: 8 },
  { label: '32×32', w: 32, h: 32 },
  { label: '64×32', w: 64, h: 32 },
  { label: '64×64', w: 64, h: 64 },
  { label: '400×300', w: 400, h: 300 },
] as const

const PALETTE_KEY = 'paper-web/palette'
const MIN_SIZE = 4
const MAX_SIZE = 1024

/** Nordic UART Service —— ESP32 BLE 透传常用服务 */
const BLE_SERVICE = '6e400001-b5a3-f393-e0a9-e50e24dcca9e'
/** RX 特征：网页 → 开发板（写） */
const BLE_CHAR_RX = '6e400002-b5a3-f393-e0a9-e50e24dcca9e'
/** TX 特征：开发板 → 网页（notify） */
const BLE_CHAR_TX = '6e400003-b5a3-f393-e0a9-e50e24dcca9e'

const BLE_CHUNK = 100
const BLE_CHUNK_DELAY = 15

function clamp(v: number, min: number, max: number) {
  return Math.min(max, Math.max(min, v))
}
function sleep(ms: number) {
  return new Promise<void>((resolve) => setTimeout(resolve, ms))
}

function isValidPalette(value: unknown): value is { hex: string; char: string; enabled?: boolean }[] {
  return (
    Array.isArray(value) &&
    value.length > 0 &&
    value.every((c) => typeof c?.hex === 'string' && typeof c?.char === 'string')
  )
}

/** 颜色集合固定为六色，本地只持久化每个颜色的填充符与启用状态 */
function loadPalette(): InkColor[] {
  const stored = new Map<string, { char: string; enabled: boolean }>()
  try {
    const raw = localStorage.getItem(PALETTE_KEY)
    if (raw) {
      const parsed: unknown = JSON.parse(raw)
      if (isValidPalette(parsed)) {
        for (const c of parsed) {
          stored.set(c.hex.toLowerCase(), { char: c.char, enabled: c.enabled !== false })
        }
      }
    }
  } catch {
    /* 忽略损坏的本地数据 */
  }
  return DEFAULT_INK_COLORS.map((c) => {
    const s = stored.get(c.hex)
    return s ? { ...c, char: s.char, enabled: s.enabled } : { ...c }
  })
}

interface Snapshot {
  w: number
  h: number
  cells: string[]
}

export const useDisplayStore = defineStore('display', () => {
  /* ------------------------------------------------------------- canvas */
  const width = ref(400)
  const height = ref(300)
  /** 大屏（如 400×300 = 12 万格）用浅响应：内容变更由 revision 统一触发 */
  const cells = shallowRef<string[]>(Array.from({ length: width.value * height.value }, () => '#ffffff'))
  const revision = ref(0)

  const tool = ref<Tool>('pen')
  const color = ref(DEFAULT_INK_COLORS[1]?.hex ?? '#1c1b18')
  const shape = ref<PixelShape>('square')
  const cursor = ref<{ x: number; y: number } | null>(null)
  /* 矩形工具选项 */
  const rectThickness = ref(1)
  const rectFilled = ref(false)
  /* 画笔/橡皮粗细 */
  const brushThickness = ref(1)

  /* --------------------------------------------------- 墨水颜色 / 占位符 */
  const palette = ref<InkColor[]>(loadPalette())

  watch(
    palette,
    (value) => {
      try {
        localStorage.setItem(PALETTE_KEY, JSON.stringify(value))
      } catch {
        /* 存储不可用时静默 */
      }
    },
    { deep: true },
  )

  /** 第一个颜色视为底色（白，固定启用），橡皮与清空都落到该颜色 */
  const bgColor = computed(() => palette.value[0]?.hex ?? '#ffffff')

  /** 当前启用的颜色（工具栏色板 / 照片转换均只用启用的颜色） */
  const activePalette = computed(() => palette.value.filter((c) => c.enabled))

  const luminance = (hex: string) => {
    const v = Number.parseInt(hex.slice(1), 16)
    return 0.299 * ((v >> 16) & 255) + 0.587 * ((v >> 8) & 255) + 0.114 * (v & 255)
  }
  const darkestColor = computed(() =>
    activePalette.value.reduce(
      (min, c) => (luminance(c.hex) < luminance(min.hex) ? c : min),
      palette.value[0] ?? DEFAULT_INK_COLORS[1]!,
    ),
  )

  const total = computed(() => width.value * height.value)

  // 当前颜色被取消启用时，回退到黑色（或第一个启用的颜色）
  watch(activePalette, (list) => {
    if (!list.some((c) => c.hex === color.value)) {
      color.value = (list.find((c) => c.hex !== bgColor.value) ?? list[0])?.hex ?? bgColor.value
    }
  })
  /** 已填充格数：大屏全量扫描贵，随 revision 防抖刷新 */
  const filled = ref(0)
  let filledTimer: ReturnType<typeof setTimeout> | undefined

  function recountFilled() {
    if (filledTimer) {
      clearTimeout(filledTimer)
      filledTimer = undefined
    }
    filled.value = cells.value.reduce((n, c) => n + (c !== bgColor.value ? 1 : 0), 0)
  }

  watch(revision, () => {
    if (filledTimer) return
    filledTimer = setTimeout(() => {
      filledTimer = undefined
      filled.value = cells.value.reduce((n, c) => n + (c !== bgColor.value ? 1 : 0), 0)
    }, 300)
  })
  // 底色变化会改变「非底色」的判定，立即重算
  watch(bgColor, recountFilled)
  recountFilled()

  function setCell(x: number, y: number, hex: string) {
    if (x < 0 || y < 0 || x >= width.value || y >= height.value) return
    const i = y * width.value + x
    if (cells.value[i] === hex) return
    cells.value[i] = hex
    revision.value++
  }

  function floodFill(sx: number, sy: number, hex: string) {
    const w = width.value
    const h = height.value
    const arr = cells.value
    const start = sy * w + sx
    const target = arr[start] ?? ''
    if (target === hex) return

    const seen = new Uint8Array(w * h)
    const stack: number[] = [start]
    while (stack.length) {
      const idx = stack.pop()
      if (idx === undefined || seen[idx]) continue
      seen[idx] = 1
      if ((arr[idx] ?? '') !== target) continue
      arr[idx] = hex
      const x = idx % w
      if (x > 0) stack.push(idx - 1)
      if (x < w - 1) stack.push(idx + 1)
      if (idx - w >= 0) stack.push(idx - w)
      if (idx + w < w * h) stack.push(idx + w)
    }
    revision.value++
  }

  /* ------------------------------------------------------------ history */
  const undoStack = ref<Snapshot[]>([])
  const redoStack = ref<Snapshot[]>([])
  const canUndo = computed(() => undoStack.value.length > 0)
  const canRedo = computed(() => redoStack.value.length > 0)

  function snapshot(): Snapshot {
    return { w: width.value, h: height.value, cells: cells.value.slice() }
  }

  function restore(s: Snapshot) {
    width.value = s.w
    height.value = s.h
    cells.value = s.cells.slice()
    cursor.value = null
    revision.value++
  }

  function pushUndo() {
    undoStack.value.push(snapshot())
    if (undoStack.value.length > 50) undoStack.value.shift()
    redoStack.value.length = 0
  }

  function undo() {
    const s = undoStack.value.pop()
    if (!s) return
    redoStack.value.push(snapshot())
    restore(s)
  }

  function redo() {
    const s = redoStack.value.pop()
    if (!s) return
    undoStack.value.push(snapshot())
    restore(s)
  }

  /* --------------------------------------------------------- operations */
  function clear() {
    pushUndo()
    cells.value = Array.from({ length: width.value * height.value }, () => bgColor.value)
    revision.value++
  }

  function invert() {
    pushUndo()
    const bg = bgColor.value
    const dark = darkestColor.value.hex
    cells.value = cells.value.map((c) => (c === bg ? dark : c === dark ? bg : c))
    revision.value++
  }

  function resize(w: number, h: number) {
    const nw = clamp(Math.round(w), MIN_SIZE, MAX_SIZE)
    const nh = clamp(Math.round(h), MIN_SIZE, MAX_SIZE)
    if (nw === width.value && nh === height.value) return

    pushUndo()
    const next: string[] = Array.from({ length: nw * nh }, () => bgColor.value)
    const copyW = Math.min(nw, width.value)
    const copyH = Math.min(nh, height.value)
    for (let y = 0; y < copyH; y++) {
      for (let x = 0; x < copyW; x++) {
        next[y * nw + x] = cells.value[y * width.value + x] ?? bgColor.value
      }
    }
    width.value = nw
    height.value = nh
    cells.value = next
    cursor.value = null
    revision.value++
  }

  function loadCells(next: string[], w = width.value, h = height.value) {
    pushUndo()
    width.value = w
    height.value = h
    cells.value = next.slice()
    cursor.value = null
    revision.value++
  }

  /* ------------------------------------------------------------- export */
  const CHAR_OF = computed(() => {
    const map = new Map<string, string>()
    for (const c of palette.value) map.set(c.hex, c.char || '0')
    return map
  })

  /** 导出/推送用的格子：手绘像素 + 放置组件图层叠加（透明不覆盖下层）。
   *  fontTick：字库异步加载完成后重算（文字点阵在加载前后不同） */
  const fontTick = ref(0)
  onFontLoaded(() => {
    fontTick.value++
    revision.value++ // 触发画布重绘文字图层
  })
  const hexRows = computed(() => {
    void fontTick.value
    const merged = flattenPlaced(cells.value, width.value, height.value)
    const rows: string[] = []
    for (let y = 0; y < height.value; y++) {
      let row = ''
      for (let x = 0; x < width.value; x++) {
        const c = merged[y * width.value + x]
        row += CHAR_OF.value.get(c ?? '') ?? '0'
      }
      rows.push(row)
    }
    return rows
  })

  const hexText = computed(() => hexRows.value.join('\n'))
  const jsonText = computed(() =>
    JSON.stringify(
      {
        width: width.value,
        height: height.value,
        colors: palette.value
          .filter((c) => c.enabled)
          .map((c) => ({ name: c.name, hex: c.hex, char: c.char })),
        pixels: hexRows.value,
      },
      null,
      2,
    ),
  )
  const byteSize = computed(() => total.value)

  /* ------------------------------------------------------ 蓝牙 (Web BLE) */
  const ble = reactive({
    supported: typeof navigator !== 'undefined' && 'bluetooth' in navigator,
    connected: false,
    connecting: false,
    deviceName: '',
    sizeReceived: false,
    pushing: false,
    /** 设备 SN（固件回复，未回复时自动生成） */
    sn: '',
    /** 云端模式：'' 未注册 / 'cloud' 已注册 / 'offline' SN 冲突只能离线 */
    cloudMode: '' as '' | 'cloud' | 'offline',
    logs: [] as string[],
  })

  let bleDevice: BluetoothDevice | null = null
  let rxChar: BluetoothRemoteGATTCharacteristic | null = null
  let txChar: BluetoothRemoteGATTCharacteristic | null = null
  let rxBuffer = ''

  function log(line: string) {
    const d = new Date()
    const t = [d.getHours(), d.getMinutes(), d.getSeconds()]
      .map((n) => String(n).padStart(2, '0'))
      .join(':')
    ble.logs.push(`${t}  ${line}`)
    if (ble.logs.length > 60) ble.logs.shift()
  }

  async function writeRaw(text: string) {
    if (!rxChar) return
    const chunks = text.match(new RegExp(`[\\s\\S]{1,${BLE_CHUNK}}`, 'g')) ?? []
    for (const chunk of chunks) {
      const bytes = new TextEncoder().encode(chunk)
      if (rxChar.properties.writeWithoutResponse || rxChar.writeValueWithoutResponse) {
        await rxChar.writeValueWithoutResponse(bytes)
      } else {
        await rxChar.writeValue(bytes)
      }
      if (chunks.length > 1) await sleep(BLE_CHUNK_DELAY)
    }
  }

  /** 解析开发板发来的文本行，SIZE 指令用于同步屏幕尺寸，SN 指令用于云端注册 */
  function handleLine(line: string) {
    const trimmed = line.trim()
    if (!trimmed) return
    const size = trimmed.match(/^SIZE[:=](\d+)\s*[x×,]\s*(\d+)/i)
    if (size) {
      const w = Number(size[1])
      const h = Number(size[2])
      if (w > 0 && h > 0) {
        resize(w, h)
        ble.sizeReceived = true
        log(`< SIZE ${w}x${h} · 屏幕尺寸已同步`)
        notify(`已读取屏幕尺寸 ${w} × ${h}`)
      }
      return
    }
    const sn = trimmed.match(/^SN[:=](.+)$/i)
    if (sn) {
      ble.sn = sn[1].trim()
      log(`< SN ${ble.sn}`)
      return
    }
    log(`< ${trimmed}`)
  }

  function onNotify(event: Event) {
    const target = event.target as BluetoothRemoteGATTCharacteristic
    const value = target.value
    if (!value) return
    rxBuffer += new TextDecoder().decode(value)
    let index = rxBuffer.indexOf('\n')
    while (index >= 0) {
      handleLine(rxBuffer.slice(0, index))
      rxBuffer = rxBuffer.slice(index + 1)
      index = rxBuffer.indexOf('\n')
    }
  }

  function cleanupBle() {
    rxChar = null
    txChar = null
    rxBuffer = ''
    if (bleDevice) {
      bleDevice.removeEventListener('gattserverdisconnected', onGattDisconnected)
      bleDevice = null
    }
    ble.connected = false
    ble.pushing = false
    ble.sizeReceived = false
    ble.sn = ''
    ble.cloudMode = ''
  }

  function onGattDisconnected() {
    const name = ble.deviceName
    cleanupBle()
    log(`! ${name || '设备'} 已断开连接`)
    notify('蓝牙已断开，请重新连接')
  }

  async function connectBluetooth() {
    if (!ble.supported) {
      notify('当前浏览器不支持 Web Bluetooth，请使用 Chrome / Edge')
      return
    }
    if (ble.connecting || ble.connected) return

    ble.connecting = true
    try {
      log('> 正在请求蓝牙设备…')
      const device = await navigator.bluetooth!.requestDevice({
        acceptAllDevices: true,
        optionalServices: [BLE_SERVICE],
      })
      bleDevice = device
      device.addEventListener('gattserverdisconnected', onGattDisconnected)

      log('> 正在建立 GATT 连接…')
      const server = await device.gatt!.connect()
      const service = await server.getPrimaryService(BLE_SERVICE)
      txChar = await service.getCharacteristic(BLE_CHAR_TX)
      rxChar = await service.getCharacteristic(BLE_CHAR_RX)
      await txChar.startNotifications()
      txChar.addEventListener('characteristicvaluechanged', onNotify)

      ble.deviceName = device.name || 'ESP32'
      ble.connected = true
      log(`< 已连接 ${ble.deviceName}`)
      notify('蓝牙连接成功')

      // 向开发板请求屏幕尺寸与 SN（设备回复 SIZE:w,h / SN:xxx）
      await writeRaw('SIZE?\n')
      await writeRaw('SN?\n')
      await registerCloudDevice()
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error)
      log(`! 连接失败 ${message}`)
      notify('蓝牙连接失败，请确认已配对后重试')
      cleanupBle()
    } finally {
      ble.connecting = false
    }
  }

  function waitFor(predicate: () => boolean, timeoutMs: number) {
    return new Promise<void>((resolve) => {
      const start = Date.now()
      const timer = setInterval(() => {
        if (predicate() || Date.now() - start > timeoutMs) {
          clearInterval(timer)
          resolve()
        }
      }, 100)
    })
  }

  /** 蓝牙连接成功后自动向云端注册设备；SN 已存在时降级为离线模式 */
  async function registerCloudDevice() {
    // 给固件一点时间回复 SIZE / SN，超时则用当前画布尺寸
    await waitFor(() => ble.sizeReceived && !!ble.sn, 2500)

    let sn = ble.sn
    if (!sn) {
      sn =
        'PI' +
        (ble.deviceName || 'ESP32')
          .toUpperCase()
          .replace(/[^A-Z0-9]/g, '')
          .slice(0, 8)
          .padEnd(8, 'X')
      log(`> 固件未回复 SN，使用生成的 ${sn}`)
      ble.sn = sn
    }

    try {
      await registerDevice({
        sn,
        name: ble.deviceName || '蓝牙墨水屏',
        width: width.value,
        height: height.value,
      })
      ble.cloudMode = 'cloud'
      log('< 云端注册成功，设备已接入云端画面')
      notify('设备已自动注册到云端')
    } catch (e) {
      ble.cloudMode = 'offline'
      const msg = e instanceof Error ? e.message : String(e)
      log(`! 云端注册失败 ${msg}`)
      if (msg.includes('已') || msg.includes('SN')) {
        notify('SN 已存在于云端，本机仅可使用离线模式（蓝牙推送）')
      } else {
        notify('云端注册失败，当前仅可使用离线模式（蓝牙推送）')
      }
    }
  }

  function disconnectBluetooth() {
    if (!ble.connected) return
    bleDevice?.gatt?.disconnect()
    cleanupBle()
    notify('已断开蓝牙连接')
  }

  async function push() {
    if (!ble.connected) {
      notify('请先连接蓝牙再推送画面')
      return
    }
    if (ble.pushing) return
    ble.pushing = true
    try {
      log(`> FRAME ${width.value}x${height.value}`)
      await writeRaw('FRAME\n')
      const rows = hexRows.value
      const step = Math.max(1, Math.floor(rows.length / 4))
      for (let i = 0; i < rows.length; i++) {
        await writeRaw(`${rows[i]}\n`)
        if (i % step === 0 && i > 0) log(`> 已发送 ${i + 1}/${rows.length} 行`)
      }
      await writeRaw('END\n')
      log('< 画面推送完成')
      notify('画面已通过蓝牙推送到屏幕')
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error)
      log(`! 推送失败 ${message}`)
      notify('推送失败，请检查蓝牙连接')
    } finally {
      ble.pushing = false
    }
  }

  /* ----------------------------------------------- WiFi 配置（写入开发板） */
  const wifi = reactive({ ssid: '', password: '' })
  const wifiWriting = ref(false)

  async function writeWifiConfig() {
    if (!ble.connected) {
      notify('请先连接蓝牙')
      return
    }
    if (wifiWriting.value) return
    if (!wifi.ssid.trim()) {
      notify('请先填写 WiFi 名称')
      return
    }
    wifiWriting.value = true
    try {
      log(`> WIFI "${wifi.ssid}"`)
      await writeRaw(`WIFI:${wifi.ssid},${wifi.password}\n`)
      log('< WiFi 配置已写入开发板')
      notify('WiFi 配置已写入开发板')
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error)
      log(`! 写入失败 ${message}`)
      notify('WiFi 配置写入失败')
    } finally {
      wifiWriting.value = false
    }
  }

  /* ------------------------------------------------------- 放置组件（可拖动/缩放/编辑） */
  const placedComponents = ref<PlacedComponent[]>([])
  const selectedPlacedUid = ref<string | null>(null)

  function addPlaced(pc: PlacedComponent) {
    placedComponents.value.push(pc)
    selectedPlacedUid.value = pc.uid
  }

  function updatePlaced(uid: string, patch: Partial<PlacedComponent>) {
    const pc = placedComponents.value.find((p) => p.uid === uid)
    if (pc) Object.assign(pc, patch)
  }

  function removePlaced(uid: string) {
    placedComponents.value = placedComponents.value.filter((p) => p.uid !== uid)
    if (selectedPlacedUid.value === uid) selectedPlacedUid.value = null
  }

  function selectPlaced(uid: string | null) {
    selectedPlacedUid.value = uid
  }

  /* -------------------------------------------------------------- modal */
  const activeModal = ref<ModalName>(null)

  function openModal(name: Exclude<ModalName, null>) {
    activeModal.value = name
  }
  function closeModal() {
    activeModal.value = null
  }

  /* -------------------------------------------------------------- toast */
  const toast = ref('')
  let toastTimer: ReturnType<typeof setTimeout> | undefined

  function notify(message: string) {
    toast.value = message
    if (toastTimer) clearTimeout(toastTimer)
    toastTimer = setTimeout(() => {
      toast.value = ''
    }, 2400)
  }

  return {
    // canvas
    width,
    height,
    cells,
    revision,
    total,
    filled,
    tool,
    color,
    shape,
    cursor,
    rectThickness,
    rectFilled,
    brushThickness,
    bgColor,
    palette,
    activePalette,
    darkestColor,
    setCell,
    floodFill,
    clear,
    invert,
    resize,
    loadCells,
    // history
    undo,
    redo,
    pushUndo,
    canUndo,
    canRedo,
    // export
    hexRows,
    hexText,
    jsonText,
    byteSize,
    // bluetooth
    ble,
    connectBluetooth,
    disconnectBluetooth,
    push,
    // wifi config
    wifi,
    wifiWriting,
    writeWifiConfig,
    // 放置组件
    placedComponents,
    selectedPlacedUid,
    addPlaced,
    updatePlaced,
    removePlaced,
    selectPlaced,
    // ui
    activeModal,
    openModal,
    closeModal,
    toast,
    notify,
  }
})

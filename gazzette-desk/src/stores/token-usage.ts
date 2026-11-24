import { defineStore } from 'pinia'
import { reactive, ref } from 'vue'

/**
 * AI 用量数据源（GLM / DeepSeek / 融合 ai.*）。
 *
 * - 配置按需进行（"用什么配什么"）：只在使用了对应组件时才需要配置该数据源；
 * - 保存到服务端 t_data_source（敏感字段 AES 加密），本地仅记住 API Key 便于回填；
 * - 画布展示一律用虚拟示例内容（见 library/variables.ts），真实数据服务端渲染时注入。
 */
export type TokenProvider = 'glm' | 'deepseek'

/** 认证方式：api_key（仅余额）/ platform_cookie（平台登录态，推荐）/ platform_password（账号密码，需谨慎） */
export type AuthType = 'api_key' | 'platform_cookie' | 'platform_password'

export interface ProviderStatus {
  configured: boolean
  authType: AuthType | null
  hasAccount: boolean
  hasCookie: boolean
}

export interface SaveAuthPayload {
  authType?: AuthType
  apiKey?: string
  account?: string
  password?: string
  cookie?: string
}

const STORAGE_KEY = 'pixelink.token-usage.keys'

const emptyStatus = (): ProviderStatus => ({
  configured: false,
  authType: null,
  hasAccount: false,
  hasCookie: false,
})

export const useTokenUsageStore = defineStore('token-usage', () => {
  /** 本地回填用（仅 API Key；账号密码/Cookie 不落本地） */
  const keys = reactive<Record<string, string>>({ glm: '', deepseek: '' })
  const status = reactive<Record<string, ProviderStatus>>({ glm: emptyStatus(), deepseek: emptyStatus() })
  /** 最近一次拉取的数据集片段（状态提示用） */
  const usage = reactive<Record<string, unknown>>({})
  const loading = reactive<Record<string, boolean>>({ glm: false, deepseek: false })
  const error = ref('')

  /* 本地恢复 */
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw) Object.assign(keys, JSON.parse(raw))
  } catch {
    /* 忽略损坏的本地数据 */
  }

  function persist() {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(keys))
    } catch {
      /* 忽略 */
    }
  }

  function providerLabel(provider: string) {
    return provider === 'glm' ? '智谱 GLM' : 'DeepSeek'
  }

  /** 组件是否已具备配置：ai.* 需要两家都配置 */
  function isReady(provider: string): boolean {
    if (provider === 'ai') {
      return status.glm.configured && status.deepseek.configured
    }
    return status[provider]?.configured ?? false
  }

  /** 保存认证配置（只提交填写的字段，服务端保留其余原值） */
  async function save(provider: TokenProvider, payload: SaveAuthPayload) {
    error.value = ''
    try {
      const res = await fetch('/api/token-usage/keys', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ provider, ...payload }),
      })
      const body = await res.json()
      if (body.code !== 0) throw new Error(body.message)
      if (payload.apiKey) {
        keys[provider] = payload.apiKey
        persist()
      }
      await loadStatus()
      await refresh(provider)
      return true
    } catch (e) {
      error.value = `${providerLabel(provider)}: ${e instanceof Error ? e.message : String(e)}`
      return false
    }
  }

  /** 拉取数据集片段（测试连通性，画布仍用虚拟内容） */
  async function refresh(provider: TokenProvider) {
    loading[provider] = true
    try {
      const res = await fetch(`/api/token-usage/usage?provider=${provider}`)
      const body = await res.json()
      if (body.code !== 0) throw new Error(body.message)
      usage[provider] = body.data?.[provider] ?? null
    } catch (e) {
      error.value = `${providerLabel(provider)}: ${e instanceof Error ? e.message : String(e)}`
    } finally {
      loading[provider] = false
    }
  }

  /** 查询服务端配置状态 */
  async function loadStatus() {
    try {
      const res = await fetch('/api/token-usage/keys')
      const body = await res.json()
      if (body.code === 0 && body.data) {
        for (const p of ['glm', 'deepseek'] as TokenProvider[]) {
          status[p] = { ...emptyStatus(), ...(body.data[p] ?? {}) }
        }
      }
    } catch {
      /* 后端不可用时忽略 */
    }
  }

  return { keys, status, usage, loading, error, isReady, providerLabel, save, refresh, loadStatus }
})

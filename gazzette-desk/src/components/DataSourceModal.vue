<script setup lang="ts">
/**
 * 数据源设置 —— API Key / 平台登录态统一配置入口。
 *
 * 「用什么配什么」：只显示当前画布上组件实际依赖的数据源
 *   - 放了 AI 用量组件 → 显示 GLM + DeepSeek
 *   - 只放了 GLM 相关   → 只显示 GLM
 *   - 未放置任何组件时   → 显示全部（从顶栏主动进入）
 *
 * DeepSeek 按认证方式显示对应字段（不把 Key/账号/密码/Cookie 一次全摆出来）：
 *   api_key            仅余额查询（官方无用量汇总接口）
 *   platform_cookie    平台登录态 Cookie（推荐，不碰账号密码）
 *   platform_password  账号密码（⚠️ 加密存服务端，可能被风控拦截）
 */
import { computed, onMounted, reactive, ref } from 'vue'
import AppModal from '@/components/AppModal.vue'
import PixelIcon from '@/components/PixelIcon.vue'
import { useDisplayStore } from '@/stores/display'
import { useTokenUsageStore, type AuthType, type TokenProvider } from '@/stores/token-usage'

const store = useDisplayStore()
const tokenStore = useTokenUsageStore()

/** 当前画布组件实际依赖的数据源（ai = 两家都要） */
const needed = computed<Set<TokenProvider>>(() => {
  const set = new Set<TokenProvider>()
  for (const pc of store.placedComponents) {
    if (!pc.comp.needsApiKey) continue
    const p = (pc.comp.attrs.provider as string) || 'ai'
    if (p === 'ai') {
      set.add('glm')
      set.add('deepseek')
    } else {
      set.add(p as TokenProvider)
    }
  }
  return set
})

const showGlm = computed(() => needed.value.size === 0 || needed.value.has('glm'))
const showDeepseek = computed(() => needed.value.size === 0 || needed.value.has('deepseek'))

/* 每个数据源的本地编辑态（仅显示当前认证方式需要的字段） */
const glm = reactive({ apiKey: '', saving: false })
const ds = reactive({
  authType: 'api_key' as AuthType,
  apiKey: '',
  account: '',
  password: '',
  cookie: '',
  saving: false,
})
const glmTest = ref('')
const dsTest = ref('')

onMounted(async () => {
  await tokenStore.loadStatus()
  glm.apiKey = tokenStore.keys.glm ?? ''
  if (tokenStore.status.deepseek.authType) {
    ds.authType = tokenStore.status.deepseek.authType
  }
})

/* GLM：仅 API Key */
async function saveGlm() {
  glm.saving = true
  glmTest.value = ''
  const ok = await tokenStore.save('glm', { authType: 'api_key', apiKey: glm.apiKey })
  glmTest.value = ok ? '✓ 已保存并拉取成功' : `✗ ${tokenStore.error || '保存失败'}`
  glm.saving = false
}

/* DeepSeek：按认证方式保存对应字段 */
async function saveDeepseek() {
  ds.saving = true
  dsTest.value = ''
  const payload =
    ds.authType === 'api_key'
      ? { authType: ds.authType, apiKey: ds.apiKey }
      : ds.authType === 'platform_cookie'
        ? { authType: ds.authType, cookie: ds.cookie }
        : { authType: ds.authType, account: ds.account, password: ds.password }
  const ok = await tokenStore.save('deepseek', payload)
  dsTest.value = ok ? '✓ 已保存并拉取成功' : `✗ ${tokenStore.error || '保存失败'}`
  ds.saving = false
}
</script>

<template>
  <AppModal title="数据源设置" subtitle="AI USAGE DATA SOURCES" @close="store.closeModal()">
    <p class="intro">
      只配置画布组件用到的数据源；敏感字段（Key/密码/Cookie）AES 加密存储在服务端。
      画布上变量一律展示虚拟示例内容，设备渲染时以真实数据注入。
    </p>

    <!-- GLM -->
    <section v-if="showGlm" class="provider">
      <header class="provider__head">
        <span class="provider__name">智谱 GLM</span>
        <span class="provider__state" :class="{ on: tokenStore.status.glm.configured }">
          {{ tokenStore.status.glm.configured ? '已配置' : '未配置' }}
        </span>
      </header>
      <div class="field">
        <label class="label" for="ds-glm-key">API Key（账单用量）</label>
        <div class="row">
          <input
            id="ds-glm-key"
            v-model="glm.apiKey"
            class="input"
            type="password"
            placeholder="粘贴智谱开放平台 API Key"
            spellcheck="false"
            autocomplete="off"
          />
          <button class="btn btn--primary" :disabled="glm.saving || !glm.apiKey" @click="saveGlm">
            保存
          </button>
        </div>
        <p v-if="glmTest" class="result" :class="{ ok: glmTest.startsWith('✓') }">{{ glmTest }}</p>
        <p v-else-if="tokenStore.loading.glm" class="hint">正在拉取用量…</p>
      </div>
    </section>

    <!-- DeepSeek -->
    <section v-if="showDeepseek" class="provider">
      <header class="provider__head">
        <span class="provider__name">DeepSeek</span>
        <span class="provider__state" :class="{ on: tokenStore.status.deepseek.configured }">
          {{ tokenStore.status.deepseek.configured ? '已配置' : '未配置' }}
        </span>
      </header>

      <div class="auth-types">
        <button
          class="chip"
          :class="{ active: ds.authType === 'api_key' }"
          @click="ds.authType = 'api_key'"
        >
          API Key · 仅余额
        </button>
        <button
          class="chip"
          :class="{ active: ds.authType === 'platform_cookie' }"
          @click="ds.authType = 'platform_cookie'"
        >
          平台 Cookie · 用量（推荐）
        </button>
        <button
          class="chip"
          :class="{ active: ds.authType === 'platform_password' }"
          @click="ds.authType = 'platform_password'"
        >
          账号密码 · 用量
        </button>
      </div>

      <!-- API Key：仅余额 -->
      <div v-if="ds.authType === 'api_key'" class="field">
        <label class="label" for="ds-ds-key">API Key</label>
        <div class="row">
          <input
            id="ds-ds-key"
            v-model="ds.apiKey"
            class="input"
            type="password"
            placeholder="sk-..."
            spellcheck="false"
            autocomplete="off"
          />
          <button class="btn btn--primary" :disabled="ds.saving || !ds.apiKey" @click="saveDeepseek">
            保存
          </button>
        </div>
        <p class="hint">官方未开放用量汇总接口，此方式只能查余额；用量请选择平台 Cookie / 账号密码方式。</p>
      </div>

      <!-- 平台 Cookie：推荐 -->
      <div v-else-if="ds.authType === 'platform_cookie'" class="field">
        <label class="label" for="ds-ds-cookie">平台登录态 Cookie</label>
        <div class="row">
          <input
            id="ds-ds-cookie"
            v-model="ds.cookie"
            class="input"
            placeholder="浏览器登录 platform.deepseek.com 后 F12 复制 Cookie"
            spellcheck="false"
            autocomplete="off"
          />
          <button class="btn btn--primary" :disabled="ds.saving || !ds.cookie" @click="saveDeepseek">
            保存
          </button>
        </div>
        <p class="hint">
          推荐：只存 Cookie，不需要账号密码。登录 platform.deepseek.com/usage →
          F12 网络面板 → 复制任意请求的 Cookie 整段粘贴。失效后重新粘贴即可。
        </p>
      </div>

      <!-- 账号密码：需明确警告 -->
      <div v-else class="field">
        <div class="warning">
          ⚠️ 账号与密码将以 AES 加密存储在本服务端数据库中，用于模拟登录换取会话。
          平台可能通过验证码/风控拦截自动登录（此时请改用 Cookie 方式）。
          <strong>如果不信任本服务端，请勿填入。</strong>
        </div>
        <label class="label" for="ds-ds-account">账号（手机号/邮箱）</label>
        <input
          id="ds-ds-account"
          v-model="ds.account"
          class="input"
          placeholder="13800000000 / you@example.com"
          spellcheck="false"
          autocomplete="off"
        />
        <label class="label" for="ds-ds-pwd">密码</label>
        <div class="row">
          <input
            id="ds-ds-pwd"
            v-model="ds.password"
            class="input"
            type="password"
            placeholder="平台登录密码"
            autocomplete="new-password"
          />
          <button
            class="btn btn--primary"
            :disabled="ds.saving || !ds.account || !ds.password"
            @click="saveDeepseek"
          >
            保存
          </button>
        </div>
      </div>
      <p v-if="dsTest" class="result" :class="{ ok: dsTest.startsWith('✓') }">{{ dsTest }}</p>
      <p v-else-if="tokenStore.loading.deepseek" class="hint">正在拉取用量…</p>
    </section>

    <div class="acts">
      <button class="btn btn--ghost btn--block" @click="store.closeModal()">
        <PixelIcon name="close" :size="14" />
        关闭
      </button>
    </div>
  </AppModal>
</template>

<style scoped>
.intro {
  color: var(--muted);
  font-size: 9.5px;
  letter-spacing: 0.04em;
  line-height: 1.6;
}

.provider {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
  border: 2px solid var(--ink);
  background: var(--paper);
}

.provider__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.provider__name {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.provider__state {
  color: var(--muted);
  font-size: 9px;
  letter-spacing: 0.1em;
}

.provider__state.on {
  color: var(--accent);
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.label {
  color: var(--ink-2);
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.08em;
}

.row {
  display: flex;
  gap: 8px;
}

.row .input {
  flex: 1;
  min-width: 0;
}

.hint {
  color: var(--muted);
  font-size: 9.5px;
  letter-spacing: 0.04em;
  line-height: 1.5;
}

.warning {
  padding: 8px 10px;
  border: 2px solid var(--accent);
  color: var(--accent);
  font-size: 9.5px;
  line-height: 1.6;
  letter-spacing: 0.04em;
}

.auth-types {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.chip {
  padding: 6px 10px;
  border: 1px solid var(--ink);
  background: var(--paper);
  color: var(--ink-2);
  font-size: 9.5px;
  font-weight: 600;
  letter-spacing: 0.06em;
  cursor: pointer;
}

.chip.active {
  background: var(--ink);
  color: var(--paper);
}

.result {
  font-size: 9.5px;
  letter-spacing: 0.04em;
  color: var(--accent);
}

.result.ok {
  color: var(--ink-2);
}

.acts {
  margin-top: 6px;
}
</style>

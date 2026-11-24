<script setup lang="ts">
import { RouterView } from 'vue-router'
import AppHeader from '@/components/AppHeader.vue'
import ExportModal from '@/components/ExportModal.vue'
import SettingsModal from '@/components/SettingsModal.vue'
import DataSourceModal from '@/components/DataSourceModal.vue'
import WifiModal from '@/components/WifiModal.vue'
import { useDisplayStore } from '@/stores/display'

const store = useDisplayStore()
</script>

<template>
  <AppHeader />
  <RouterView />

  <Transition name="toast">
    <div v-if="store.toast" class="toast">{{ store.toast }}</div>
  </Transition>

  <WifiModal v-if="store.activeModal === 'wifi'" />
  <ExportModal v-if="store.activeModal === 'export'" />
  <SettingsModal v-if="store.activeModal === 'settings'" />
  <DataSourceModal v-if="store.activeModal === 'datasource'" />
</template>

<style scoped>
.toast {
  position: fixed;
  bottom: 28px;
  left: 50%;
  z-index: 90;
  transform: translateX(-50%);
  padding: 10px 18px;
  border: 2px solid var(--ink);
  background: var(--ink);
  color: var(--paper);
  font-size: 11px;
  letter-spacing: 0.1em;
  box-shadow: 4px 4px 0 var(--accent);
  white-space: nowrap;
}

.toast-enter-active,
.toast-leave-active {
  transition:
    opacity 0.18s ease,
    transform 0.18s ease;
}

.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translate(-50%, 10px);
}

@media (max-width: 720px) {
  .toast {
    max-width: calc(100vw - 28px);
    white-space: normal;
    text-align: center;
  }
}
</style>

<script setup lang="ts">
import PixelIcon from '@/components/PixelIcon.vue'

withDefaults(
  defineProps<{
    title: string
    subtitle?: string
  }>(),
  { subtitle: '' },
)

const emit = defineEmits<{ close: [] }>()
</script>

<template>
  <Teleport to="body">
    <div class="modal" role="dialog" aria-modal="true" @click.self="emit('close')">
      <div class="modal__card">
        <header class="modal__head">
          <div>
            <h2 class="modal__title">{{ title }}</h2>
            <p v-if="subtitle" class="modal__sub">{{ subtitle }}</p>
          </div>
          <button class="iconbtn" title="关闭" @click="emit('close')">
            <PixelIcon name="close" :size="14" />
          </button>
        </header>
        <div class="modal__body">
          <slot />
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.modal {
  position: fixed;
  inset: 0;
  z-index: 80;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding: 8vh 16px 32px;
  background: rgba(29, 28, 24, 0.4);
  overflow-y: auto;
}

.modal__card {
  width: 100%;
  max-width: 440px;
  border: 2px solid var(--ink);
  background: var(--paper-2);
  box-shadow: var(--shadow-lg);
}

.modal__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border-bottom: 2px solid var(--ink);
  background: var(--paper-2);
}

.modal__title {
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.06em;
}

.modal__sub {
  margin-top: 3px;
  color: var(--muted);
  font-size: 9px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.modal__body {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 16px 14px;
}
</style>

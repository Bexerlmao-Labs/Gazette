<script setup lang="ts">
defineProps<{
  num: string
  title: string
  subtitle?: string
}>()
</script>

<template>
  <section class="card">
    <header class="card__head">
      <div class="card__titlewrap">
        <span class="card__num">{{ num }}</span>
        <div class="card__text">
          <h2 class="card__title">{{ title }}</h2>
          <p v-if="subtitle" class="card__sub">{{ subtitle }}</p>
        </div>
      </div>
      <div class="card__action">
        <slot name="action" />
      </div>
    </header>
    <div class="card__body">
      <slot />
    </div>
  </section>
</template>

<style scoped>
.card {
  position: relative;
  background: var(--paper-2);
  border: 2px solid var(--ink);
  box-shadow: var(--shadow-lg);
}

.card::after {
  content: '';
  position: absolute;
  top: 0;
  right: 0;
  width: 7px;
  height: 7px;
  background: var(--ink);
}

.card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 12px 14px;
  border-bottom: 2px solid var(--ink);
  background: var(--paper-2);
}

.card__titlewrap {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.card__num {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  flex: none;
  border: 2px solid var(--ink);
  background: var(--accent-soft);
  color: var(--ink);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.04em;
}

.card__text {
  min-width: 0;
}

.card__title {
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.06em;
  line-height: 1.2;
  white-space: nowrap;
}

.card__sub {
  margin-top: 3px;
  color: var(--muted);
  font-size: 9px;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

.card__action {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: none;
}

.card__body {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 16px 14px;
}
</style>

<script setup lang="ts">
import CanvasPanel from '@/components/CanvasPanel.vue'
</script>

<template>
  <div class="canvas-view">
    <CanvasPanel />
  </div>
</template>

<style scoped>
.canvas-view {
  width: 100%;
  max-width: 1040px;
  margin: 0 auto;
  padding: 22px 24px 30px;
  display: flex;
  flex-direction: column;
}

@media (max-width: 900px) {
  .canvas-view {
    padding: 16px 14px 24px;
  }
}
</style>

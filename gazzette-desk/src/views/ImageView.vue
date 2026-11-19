<script setup lang="ts">
import ImagePanel from '@/components/ImagePanel.vue'
</script>

<template>
  <div class="image-view">
    <ImagePanel />
  </div>
</template>

<style scoped>
.image-view {
  width: 100%;
  max-width: 520px;
  margin: 0 auto;
  padding: 22px 24px 30px;
  display: flex;
  flex-direction: column;
}

@media (max-width: 900px) {
  .image-view {
    padding: 16px 14px 24px;
  }
}
</style>

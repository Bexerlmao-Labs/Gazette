<script setup lang="ts">
import { ref } from 'vue'
import CanvasPanel from '@/components/CanvasPanel.vue'
import DrawToolbar from '@/components/DrawToolbar.vue'
import ComponentLibrary from '@/components/library/ComponentLibrary.vue'
import ComponentPreviewModal from '@/components/library/ComponentPreviewModal.vue'
import type { LibComponent } from '@/components/library/library'

const previewComp = ref<LibComponent | null>(null)
</script>

<template>
  <!-- 三栏工作区：左侧工具 / 中间画布（占满）/ 右侧组件库 -->
  <div class="workspace">
    <aside class="workspace__side workspace__side--left">
      <DrawToolbar />
    </aside>

    <main class="workspace__main">
      <CanvasPanel />
    </main>

    <aside class="workspace__side workspace__side--right">
      <ComponentLibrary @preview="previewComp = $event" />
    </aside>
  </div>

  <ComponentPreviewModal v-if="previewComp" :comp="previewComp" @close="previewComp = null" />
</template>

<style scoped>
.workspace {
  display: flex;
  align-items: stretch;
  height: calc(100vh - 68px);
}

.workspace__side {
  width: 210px;
  flex: none;
  overflow-y: auto;
  overflow-x: hidden;
  background: var(--paper-2);
}

.workspace__side--left {
  padding: 14px 12px;
  border-right: 2px solid var(--ink);
}

.workspace__side--right {
  border-left: 2px solid var(--ink);
}

.workspace__main {
  flex: 1;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

@media (max-width: 900px) {
  .workspace {
    flex-direction: column;
    height: auto;
  }

  .workspace__side {
    width: 100%;
    overflow: visible;
  }

  .workspace__side--left {
    order: 2;
    border-right: none;
    border-top: 2px solid var(--ink);
  }

  .workspace__side--right {
    order: 3;
    border-left: none;
    border-top: 2px solid var(--ink);
  }

  .workspace__main {
    order: 1;
  }
}
</style>

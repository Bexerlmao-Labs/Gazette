<script setup lang="ts">
withDefaults(defineProps<{ name: string; size?: number }>(), { size: 16 })
</script>

<template>
  <svg
    class="icon"
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.8"
    stroke-linecap="square"
    stroke-linejoin="miter"
    aria-hidden="true"
  >
    <template v-if="name === 'pen'">
      <path d="M4 20v-4l10-10 4 4-10 10z" />
      <path d="M13 7l4 4" />
    </template>

    <template v-else-if="name === 'eraser'">
      <path d="M5 15l7-7 6 6-4 4H9z" />
      <path d="M10 20h10" />
    </template>

    <template v-else-if="name === 'fill'">
      <path d="M6 11l6-6 6 6-6 6z" />
      <path d="M9 20h10" />
      <path d="M19 8v4" />
    </template>

    <template v-else-if="name === 'picker'">
      <path d="M4 20l2-1 8-8-1-1-8 8z" />
      <path d="M14 5l5 5 2-2-5-5z" />
    </template>

    <template v-else-if="name === 'rect'">
      <rect x="4" y="6" width="16" height="12" />
    </template>

    <template v-else-if="name === 'line'">
      <path d="M4 20L20 4" />
    </template>

    <template v-else-if="name === 'undo'">
      <path d="M8 6L4 10l4 4" />
      <path d="M4 10h10a5 5 0 0 1 0 10h-4" />
    </template>

    <template v-else-if="name === 'redo'">
      <path d="M16 6l4 4-4 4" />
      <path d="M20 10H10a5 5 0 0 0 0 10h4" />
    </template>

    <template v-else-if="name === 'trash'">
      <path d="M4 7h16" />
      <path d="M9 7V4h6v3" />
      <path d="M6 7l1 13h10l1-13" />
    </template>

    <template v-else-if="name === 'invert'">
      <circle cx="12" cy="12" r="8" />
      <path d="M12 4a8 8 0 0 1 0 16z" fill="currentColor" stroke="none" />
    </template>

    <template v-else-if="name === 'send'">
      <path d="M3 12l17-8-6 17-3-7z" />
      <path d="M11 14l9-10" />
    </template>

    <template v-else-if="name === 'upload'">
      <path d="M12 16V4" />
      <path d="M7 9l5-5 5 5" />
      <path d="M4 20h16" />
    </template>

    <template v-else-if="name === 'copy'">
      <rect x="8" y="8" width="12" height="12" />
      <path d="M16 8V4H4v12h4" />
    </template>

    <template v-else-if="name === 'eye'">
      <path d="M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12z" />
      <circle cx="12" cy="12" r="3" />
    </template>

    <template v-else-if="name === 'eyeOff'">
      <path d="M2 12s4-7 10-7c1.8 0 3.4.5 4.8 1.3" />
      <path d="M22 12s-4 7-10 7c-1.8 0-3.4-.5-4.8-1.3" />
      <path d="M4 4l16 16" />
    </template>

    <template v-else-if="name === 'wifi'">
      <path d="M3 9a13 13 0 0 1 18 0" />
      <path d="M7 13a8 8 0 0 1 10 0" />
      <path d="M10 17a4 4 0 0 1 4 0" />
      <path d="M12 20h.01" stroke-width="2.6" />
    </template>

    <template v-else-if="name === 'check'">
      <path d="M4 12l5 5L20 6" />
    </template>

    <template v-else-if="name === 'screen'">
      <rect x="3" y="4" width="18" height="13" />
      <path d="M9 21h6" />
      <path d="M12 17v4" />
    </template>

    <template v-else-if="name === 'image'">
      <rect x="3" y="4" width="18" height="16" />
      <path d="M3 16l5-5 4 4 3-3 6 6" />
      <circle cx="8" cy="9" r="1.4" />
    </template>

    <template v-else-if="name === 'plug'">
      <path d="M9 3v5M15 3v5" />
      <path d="M6 8h12v3a6 6 0 0 1-12 0z" />
      <path d="M12 17v4" />
    </template>

    <template v-else-if="name === 'code'">
      <path d="M9 6l-6 6 6 6" />
      <path d="M15 6l6 6-6 6" />
    </template>

    <template v-else-if="name === 'download'">
      <path d="M12 4v12" />
      <path d="M7 11l5 5 5-5" />
      <path d="M4 20h16" />
    </template>

    <template v-else-if="name === 'close'">
      <path d="M6 6l12 12M18 6L6 18" />
    </template>

    <template v-else-if="name === 'spark'">
      <path d="M12 3v6M12 15v6M3 12h6M15 12h6" />
      <path d="M6 6l3 3M15 15l3 3M18 6l-3 3M9 15l-3 3" />
    </template>

    <template v-else-if="name === 'bluetooth'">
      <path d="M7 7l10 10-5 5V2l5 5L7 17" />
    </template>

    <template v-else-if="name === 'gear'">
      <circle cx="12" cy="12" r="3.2" />
      <path d="M12 2v3M12 19v3M2 12h3M19 12h3" />
      <path d="M4.9 4.9l2.2 2.2M16.9 16.9l2.2 2.2M19.1 4.9L16.9 7.1M7.1 16.9l-2.2 2.2" />
    </template>
  </svg>
</template>

<style scoped>
.icon {
  flex: none;
}
</style>

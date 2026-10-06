<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useMediaQuery } from '@vueuse/core'
import { Toaster } from 'vue-sonner'
import 'vue-sonner/style.css'
import { usePreferencesStore } from '@/stores/preferences'

const preferences = usePreferencesStore()
const mobile = useMediaQuery('(max-width: 640px)')
const route = useRoute()
const position = computed(() => (mobile.value ? 'top-center' : 'top-right'))
// Full-screen editors stack two toolbars (header + pane bar); keep toasts clear of their controls.
const offset = computed(() => ({ top: route?.meta.focus ? 124 : 68, right: 20 }))
</script>

<template>
  <Toaster
    :theme="preferences.resolvedTheme"
    :position="position"
    :visible-toasts="4"
    :offset="offset"
    :gap="10"
    close-button
    rich-colors
    :toast-options="{ class: 'jp-toast' }"
  />
</template>

<style>
[data-sonner-toaster] {
  --font: var(--font-sans);
  font-family: var(--font-sans);
  z-index: var(--z-toast) !important;
}

[data-sonner-toaster] .jp-toast {
  border-radius: var(--radius-md);
  border: 1px solid var(--border-subtle);
  box-shadow: var(--shadow-lg);
  font-size: var(--fs-sm);
  line-height: var(--lh-sm);
  padding: 12px 14px;
  gap: 10px;
}

[data-sonner-toaster][data-sonner-theme='light'] {
  --normal-bg: var(--surface-overlay);
  --normal-text: var(--text-primary);
  --normal-border: var(--border-subtle);
  --success-bg: #f1faf6;
  --success-border: color-mix(in srgb, var(--color-success) 26%, transparent);
  --success-text: var(--color-success-text);
  --error-bg: #fff5f5;
  --error-border: color-mix(in srgb, var(--color-danger) 26%, transparent);
  --error-text: var(--color-danger-text);
  --warning-bg: #fffaf0;
  --warning-border: color-mix(in srgb, var(--color-warning) 30%, transparent);
  --warning-text: var(--color-warning-text);
  --info-bg: var(--ink-50);
  --info-border: var(--color-primary-border);
  --info-text: var(--color-info-text);
}

[data-sonner-toaster] [data-button] {
  border-radius: var(--radius-sm) !important;
  font-weight: 600 !important;
  background: var(--color-primary) !important;
  color: #fff !important;
}
</style>

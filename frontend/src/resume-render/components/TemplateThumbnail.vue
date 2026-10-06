<script setup lang="ts">
/**
 * First page of a template rendered live with the real renderer components, mounted only once it
 * scrolls into view (galleries show 16 of these). Thumbnails are never interactive.
 */
import { ref, shallowRef, watch } from 'vue'
import { useIntersectionObserver } from '@vueuse/core'
import type { TemplateModule } from '../templates/manifest'
import { loadTemplate } from '../templates/registry'
import ResumeDocument from './ResumeDocument.vue'

const props = withDefaults(defineProps<{
  templateId: string
  content: unknown
  design?: unknown
  photo?: string | null
  /** Accessible description of what the thumbnail shows. */
  label?: string
}>(), { design: undefined, photo: null, label: '' })

const root = ref<HTMLElement | null>(null)
const visible = ref(false)
const module = shallowRef<TemplateModule | null>(null)
const failed = ref(false)

const { stop } = useIntersectionObserver(root, ([entry]) => {
  if (entry?.isIntersecting) {
    visible.value = true
    stop()
  }
}, { rootMargin: '200px' })

watch([visible, () => props.templateId], async ([shown, id]) => {
  if (!shown) return
  failed.value = false
  try {
    const loaded = await loadTemplate(id)
    if (props.templateId === id) module.value = loaded
  } catch {
    failed.value = true
  }
}, { immediate: true })
</script>

<template>
  <div ref="root" class="rr-thumb" role="img" :aria-label="label || undefined">
    <ResumeDocument
      v-if="module"
      :template="module"
      :content="content"
      :design="design"
      :photo="photo"
      mode="thumbnail"
      aria-hidden="true"
    />
    <span v-else-if="failed" class="rr-thumb__state">预览加载失败</span>
    <span v-else class="rr-thumb__state rr-thumb__state--pending" aria-hidden="true" />
  </div>
</template>

<style>
.rr-thumb {
  position: relative;
  width: 100%;
  aspect-ratio: 210 / 297;
  overflow: hidden;
  background: var(--sheet-bg, white);
}
.rr-thumb .rr-root { pointer-events: none; }
.rr-thumb__state {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  color: var(--text-tertiary);
  font-size: 12px;
}
.rr-thumb__state--pending {
  background: linear-gradient(100deg, var(--surface-2) 30%, var(--surface-3, var(--surface-2)) 50%, var(--surface-2) 70%) 0 0 / 200% 100%;
  animation: rr-thumb-shimmer 1.4s linear infinite;
}
@keyframes rr-thumb-shimmer { to { background-position: -200% 0; } }
@media (prefers-reduced-motion: reduce) { .rr-thumb__state--pending { animation: none; } }
</style>

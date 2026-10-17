<script setup lang="ts">
/**
 * A resume's real first page, drawn by the same renderer as the preview and the PDF. The render
 * model is fetched only once the card scrolls into view; until a layout exists the slot shows.
 */
import { computed, ref } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { useIntersectionObserver } from '@vueuse/core'
import TemplateThumbnail from '@/resume-render/components/TemplateThumbnail.vue'
import { hasTemplate } from '@/resume-render/templates/registry'
import { fetchResumeThumbnail } from '../services/resumeApi'

const props = defineProps<{ resumeId: string; updatedAt?: string | null; title?: string }>()

const root = ref<HTMLElement | null>(null)
const visible = ref(false)
const { stop } = useIntersectionObserver(root, ([entry]) => {
  if (entry?.isIntersecting) {
    visible.value = true
    stop()
  }
}, { rootMargin: '160px' })

const model = useQuery({
  queryKey: computed(() => ['resume-thumbnail', props.resumeId, props.updatedAt ?? ''] as const),
  queryFn: ({ signal }) => fetchResumeThumbnail(props.resumeId, signal),
  enabled: visible,
  staleTime: 5 * 60_000,
  retry: 1,
})
const ready = computed(() => {
  const data = model.data.value
  return data && hasTemplate(data.templateId) ? data : null
})
</script>

<template>
  <div ref="root" class="resume-thumb">
    <TemplateThumbnail
      v-if="ready"
      :template-id="ready.templateId"
      :content="ready.content"
      :design="ready.design"
      :photo="ready.photo ?? null"
      :label="title ? `${title}的第一页` : '简历第一页'"
    />
    <span v-else-if="!visible || model.isPending.value" class="resume-thumb__pending" aria-hidden="true" />
    <slot v-else />
  </div>
</template>

<style scoped>
.resume-thumb {
  position: relative;
  width: 100%;
  height: 100%;
}

.resume-thumb__pending {
  position: absolute;
  inset: 0;
  background: linear-gradient(100deg, var(--surface-2) 30%, var(--surface-3) 50%, var(--surface-2) 70%) 0 0 / 200% 100%;
  animation: resume-thumb-shimmer 1.4s linear infinite;
}

@keyframes resume-thumb-shimmer {
  to {
    background-position: -200% 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .resume-thumb__pending {
    animation: none;
  }
}
</style>

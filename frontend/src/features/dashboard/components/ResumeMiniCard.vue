<script setup lang="ts">
import { computed } from 'vue'
import { ArrowUpRight } from 'lucide-vue-next'
import { formatRelative } from '@/shared/lib/datetime'
import type { RecentResume } from '../types'

const props = defineProps<{ resume: RecentResume }>()
const total = computed(() => props.resume.totalModules || 11)
const progress = computed(() => Math.min(1, props.resume.confirmedModules / total.value))
// A deterministic accent per resume keeps the miniature papers visually distinct.
const ACCENTS = ['#4a44d9', '#ef7339', '#13a06f', '#9b6bf2', '#2f6fb3']
const accent = computed(() => {
  let hash = 0
  for (const char of props.resume.id) hash = (hash * 33 + char.charCodeAt(0)) >>> 0
  return ACCENTS[hash % ACCENTS.length]
})
</script>

<template>
  <RouterLink :to="`/resumes/${encodeURIComponent(resume.id)}`" class="mini" :style="{ '--accent': accent }">
    <div class="mini__paper" aria-hidden="true">
      <div class="mini__head">
        <span class="mini__avatar" />
        <span class="mini__name" />
      </div>
      <span v-for="n in 3" :key="`a${n}`" class="mini__line" :style="{ width: `${92 - n * 14}%` }" />
      <span class="mini__section" />
      <span v-for="n in 4" :key="`b${n}`" class="mini__line" :style="{ width: `${88 - (n % 3) * 18}%` }" />
      <span class="mini__section" />
      <span v-for="n in 2" :key="`c${n}`" class="mini__line" :style="{ width: `${80 - n * 16}%` }" />
      <span class="mini__open"><ArrowUpRight :size="15" /></span>
    </div>
    <div class="mini__meta">
      <strong :title="resume.title">{{ resume.title }}</strong>
      <small>{{ resume.templateName || '智能模板' }} · {{ formatRelative(resume.updatedAt) }}</small>
      <span class="mini__progress" :title="`已确认 ${resume.confirmedModules}/${total} 个模块`">
        <span :style="{ transform: `scaleX(${progress})` }" />
      </span>
    </div>
  </RouterLink>
</template>

<style scoped>
.mini {
  display: grid;
  gap: var(--space-3);
  padding: var(--space-3);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  background: var(--surface-1);
  box-shadow: var(--shadow-sm);
  color: var(--text-primary);
  transition: transform var(--dur-base) var(--ease-out), box-shadow var(--dur-base) var(--ease-out), border-color var(--dur-base);
}

.mini:hover {
  transform: translateY(-3px);
  border-color: var(--border-default);
  box-shadow: var(--shadow-md);
  color: var(--text-primary);
}

.mini__paper {
  position: relative;
  aspect-ratio: 210 / 150;
  display: flex;
  flex-direction: column;
  gap: 6px;
  overflow: hidden;
  padding: 14px 16px;
  border-radius: var(--radius-sm);
  background: linear-gradient(180deg, #fff, #fdfcfa);
  box-shadow: inset 0 0 0 1px rgba(30, 28, 25, 0.06);
}

.mini__head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.mini__avatar {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: color-mix(in srgb, var(--accent) 22%, #fff);
}

.mini__name {
  width: 42%;
  height: 7px;
  border-radius: 4px;
  background: var(--accent);
}

.mini__section {
  width: 28%;
  height: 5px;
  margin-top: 4px;
  border-radius: 3px;
  background: color-mix(in srgb, var(--accent) 60%, #fff);
}

.mini__line {
  height: 4px;
  border-radius: 3px;
  background: #e9e6df;
}

.mini__open {
  position: absolute;
  right: 8px;
  top: 8px;
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: var(--color-primary);
  color: #fff;
  opacity: 0;
  transform: scale(0.8);
  transition: opacity var(--dur-base) var(--ease-out), transform var(--dur-base) var(--ease-spring);
}

.mini:hover .mini__open {
  opacity: 1;
  transform: scale(1);
}

.mini__meta {
  display: grid;
  gap: 2px;
  padding: 0 2px 2px;
}

.mini__meta strong {
  overflow: hidden;
  font-size: var(--fs-body);
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mini__meta small {
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
}

.mini__progress {
  position: relative;
  height: 4px;
  margin-top: 6px;
  overflow: hidden;
  border-radius: 999px;
  background: var(--surface-3);
}

.mini__progress span {
  position: absolute;
  inset: 0;
  border-radius: inherit;
  background: var(--gradient-ai);
  transform-origin: left;
  transition: transform var(--dur-slower) var(--ease-out);
}
</style>

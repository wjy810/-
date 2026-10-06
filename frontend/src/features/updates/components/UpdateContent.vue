<script setup lang="ts">
import { AlertTriangle, CheckCircle2, Lightbulb, Rocket, ShieldCheck, Wrench } from 'lucide-vue-next'
import { sectionLabel } from '../labels'
import type { UpdateSection } from '../types'

withDefaults(defineProps<{
  sections: UpdateSection[]
  compact?: boolean
  assetUrl?: (assetId: string) => string
}>(), {
  assetUrl: (assetId: string) => `/api/v1/updates/assets/${encodeURIComponent(assetId)}`,
})

function icon(type: string) {
  return ({
    HIGHLIGHTS: Rocket,
    FEATURES: Lightbulb,
    IMPROVEMENTS: CheckCircle2,
    FIXES: Wrench,
    IMPORTANT: AlertTriangle,
    COMPATIBILITY: ShieldCheck,
  } as Record<string, typeof Rocket>)[type] ?? Lightbulb
}
</script>

<template>
  <div class="update-content" :class="{ 'is-compact': compact }">
    <section
      v-for="(section, index) in sections"
      :id="`section-${section.id || index}`"
      :key="section.id || `${section.sectionType}-${section.sortOrder}`"
      class="update-section"
      :class="`is-${section.sectionType.toLowerCase()}`"
    >
      <header>
        <span class="update-section__icon"><component :is="icon(section.sectionType)" :size="18" /></span>
        <div>
          <small>{{ sectionLabel(section.sectionType) }}</small>
          <h2>{{ section.title }}</h2>
        </div>
      </header>
      <p v-if="section.body" class="update-section__body">{{ section.body }}</p>
      <figure v-if="section.imageAssetId" class="update-section__media">
        <img :src="assetUrl(section.imageAssetId)" :alt="section.imageAlt || section.title" loading="lazy" />
        <figcaption v-if="section.imageAlt">{{ section.imageAlt }}</figcaption>
      </figure>
      <ul v-if="section.items?.length">
        <li v-for="item in section.items" :key="item"><CheckCircle2 :size="15" /> <span>{{ item }}</span></li>
      </ul>
    </section>
  </div>
</template>

<style scoped>
.update-section__media { margin: 16px 0 0; }
.update-section__media img { display: block; width: 100%; max-height: 540px; object-fit: contain; border: 1px solid #d9e2ef; border-radius: 8px; background: #f7f9fc; }
.update-section__media figcaption { margin-top: 7px; color: #74849c; font-size: 12px; line-height: 1.5; }
.is-compact .update-section__media img { max-height: 260px; }
</style>

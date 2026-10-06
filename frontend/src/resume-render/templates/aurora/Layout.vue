<script setup lang="ts">
import { computed } from 'vue'
import type { LayoutProps } from '../../components/context'
import ContactList from '../../components/primitives/ContactList.vue'
import ResumePhoto from '../../components/primitives/ResumePhoto.vue'
import RRegion from '../../components/RRegion.vue'
import { artUrl } from '../../assets/art'

const props = defineProps<LayoutProps>()
/* One aurora asset, hue-rotated per palette (filters are rasterised identically in preview and print). */
const HUE: Record<string, number> = { stardust: 0, 'clear-sky': -38, peach: 112 }
const glow = computed(() => ({ backgroundImage: `url(${artUrl('aurora-soft')})`, filter: `hue-rotate(${HUE[props.design.paletteId] ?? 0}deg) saturate(0.9)` }))
</script>

<template>
  <span v-if="!page.continuation" class="rr-decor au-glow" :style="glow" aria-hidden="true" />
  <div class="rr-sheet">
    <header v-if="!page.continuation" class="au-head">
      <div class="au-identity">
        <h1 class="au-name">{{ document.header.name }}</h1>
        <p v-if="document.header.title" class="au-title">{{ document.header.title }}</p>
        <ContactList :header="document.header" :icons="design.contactIcons" class="au-contact" />
      </div>
      <ResumePhoto v-if="document.header.photo && design.headerVariant === 'photo'" :src="document.header.photo" :shape="design.photo.shape" class="au-photo" />
    </header>
    <div v-else class="rr-continue"><strong>{{ document.header.name }}</strong><span>{{ document.header.title }}</span></div>
    <div class="rr-body"><RRegion id="main" :locale="document.locale" /></div>
  </div>
</template>

<style>
[data-template='aurora'] .au-glow { top: -24mm; right: -26mm; width: 156mm; height: 156mm; background: no-repeat center / contain; opacity: 0.85; }
[data-template='aurora'] .au-head { display: flex; align-items: flex-end; justify-content: space-between; gap: 8mm; margin: 4mm 0 8mm; }
[data-template='aurora'] .au-name { font-size: 32pt; font-weight: 300; line-height: 1.05; letter-spacing: 0.06em; color: var(--r-ink); }
[data-template='aurora'] .au-title { margin-top: 2.4mm; color: var(--r-accent); font-size: calc(var(--r-size) + 2pt); font-weight: 500; letter-spacing: 0.04em; }
[data-template='aurora'] .au-contact { margin-top: 4mm; }
[data-template='aurora'] .rr-heading {
  flex-direction: column;
  align-items: flex-start;
  gap: 1.4mm;
  color: var(--r-ink);
  font-size: calc(var(--r-size) + 1.5pt);
  font-weight: 600;
  letter-spacing: 0.08em;
}
[data-template='aurora'] .rr-heading::before { content: ''; width: 10mm; height: 1.6pt; border-radius: 1pt; background: var(--r-accent); }
[data-template='aurora'] .rr-entry__subtitle { color: var(--r-accent); }
</style>

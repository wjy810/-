<script setup lang="ts">
import { computed } from 'vue'
import type { LayoutProps } from '../../components/context'
import ContactList from '../../components/primitives/ContactList.vue'
import RRegion from '../../components/RRegion.vue'
import { artUrl } from '../../assets/art'

const props = defineProps<LayoutProps>()
const paper = computed(() => ({ backgroundImage: `url(${artUrl(props.theme.palette.art ?? 'paper-warm')})` }))
</script>

<template>
  <span class="rr-decor ed-paper" :style="paper" aria-hidden="true" />
  <div class="rr-sheet">
    <header v-if="!page.continuation" class="ed-head">
      <h1 class="ed-name">{{ document.header.name }}</h1>
      <p v-if="document.header.title" class="ed-title">{{ document.header.title }}</p>
      <ContactList :header="document.header" :icons="false" separator="/" class="ed-contact" />
    </header>
    <div v-else class="rr-continue"><strong>{{ document.header.name }}</strong><span>{{ document.header.title }}</span></div>
    <div class="rr-body"><RRegion id="main" :locale="document.locale" /></div>
  </div>
</template>

<style>
[data-template='editorial'] .ed-paper { inset: 0; background: no-repeat center / cover; }
[data-template='editorial'] .ed-head { margin-bottom: 8mm; text-align: center; }
[data-template='editorial'][data-header='left'] .ed-head { text-align: left; }
[data-template='editorial'] .ed-name { font-family: var(--r-font-heading); font-size: 33pt; font-weight: 600; line-height: 1.05; letter-spacing: 0.12em; color: var(--r-ink); }
[data-template='editorial'] .ed-title {
  display: inline-block;
  margin-top: 3mm;
  padding: 1.2mm 0;
  border-top: 0.5pt solid var(--r-ink);
  border-bottom: 0.5pt solid var(--r-ink);
  color: var(--r-accent);
  font-family: var(--r-font-heading);
  font-size: calc(var(--r-size) + 1pt);
  letter-spacing: 0.3em;
}
[data-template='editorial'] .ed-contact { justify-content: center; margin-top: 3.4mm; color: var(--r-ink); }
[data-template='editorial'][data-header='left'] .ed-contact { justify-content: flex-start; }
[data-template='editorial'] .rr-heading {
  align-items: baseline;
  gap: 3mm;
  padding-bottom: 1.6mm;
  border-bottom: 0.5pt solid color-mix(in srgb, var(--r-ink) 35%, transparent);
  font-size: calc(var(--r-size) + 2pt);
  font-weight: 600;
  letter-spacing: 0.18em;
}
[data-template='editorial'] .rr-heading__index { display: inline; color: var(--r-accent); font-family: var(--r-font-heading); font-size: calc(var(--r-size) + 5pt); font-style: italic; font-weight: 400; letter-spacing: 0; }
[data-template='editorial'] .rr-entry__title { font-family: var(--r-font-heading); font-size: calc(var(--r-size) + 1.25pt); }
[data-template='editorial'] .rr-entry__subtitle { font-style: italic; font-weight: 400; color: var(--r-accent); }
[data-template='editorial'] .rr-bullets li::before { background: var(--r-accent); border-radius: 0; width: 3pt; height: 0.8pt; margin-top: calc(var(--r-size) * var(--r-leading) * 0.5); }
</style>

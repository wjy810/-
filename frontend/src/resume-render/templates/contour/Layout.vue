<script setup lang="ts">
import type { LayoutProps } from '../../components/context'
import ContactList from '../../components/primitives/ContactList.vue'
import RRegion from '../../components/RRegion.vue'
import { artUrl } from '../../assets/art'

defineProps<LayoutProps>()
const lines = { backgroundImage: `url(${artUrl('topo-lines')})` }
</script>

<template>
  <span class="rr-decor ct-lines" :style="lines" aria-hidden="true" />
  <div class="rr-sheet">
    <header v-if="!page.continuation" class="ct-head">
      <p class="ct-tag">
        <span v-if="document.header.location">{{ document.header.location }}</span>
        <span v-if="document.header.title">{{ document.header.title }}</span>
      </p>
      <h1 class="ct-name">{{ document.header.name }}</h1>
      <ContactList :header="document.header" :icons="design.contactIcons" :with-location="false" class="ct-contact" />
    </header>
    <div v-else class="rr-continue"><strong>{{ document.header.name }}</strong><span>{{ document.header.title }}</span></div>
    <div class="rr-body"><RRegion id="main" :locale="document.locale" /></div>
  </div>
</template>

<style>
[data-template='contour'] .ct-lines { top: -10mm; right: -14mm; width: 92mm; height: 170mm; background: no-repeat right top / contain; opacity: 0.5; }
[data-template='contour'] .ct-head { margin-bottom: 7mm; }
[data-template='contour'] .ct-tag { display: flex; gap: 3mm; color: var(--r-accent); font-family: var(--r-font-mono); font-size: calc(var(--r-size) - 1pt); font-weight: 500; letter-spacing: 0.08em; }
[data-template='contour'] .ct-tag span + span::before { content: '/'; margin-right: 3mm; color: var(--r-muted); }
[data-template='contour'] .ct-name { margin-top: 2mm; font-size: 30pt; font-weight: 700; line-height: 1.05; letter-spacing: 0.02em; }
[data-template='contour'] .ct-contact { margin-top: 3.4mm; }
[data-template='contour'] .rr-heading { gap: 2.4mm; color: var(--r-ink); font-size: calc(var(--r-size) + 1.25pt); }
[data-template='contour'] .rr-heading__index { display: inline; color: var(--r-accent); font-family: var(--r-font-mono); font-size: calc(var(--r-size) - 0.5pt); font-weight: 500; }
[data-template='contour'] .rr-heading::after { content: ''; align-self: center; flex: 1; height: 0.5pt; margin-left: 1mm; background: var(--r-rule); }
[data-template='contour'] .rr-entry__date { font-family: var(--r-font-mono); font-size: calc(var(--r-size) - 1.25pt); }
[data-template='contour'] .rr-entry__subtitle { color: var(--r-accent); }
</style>

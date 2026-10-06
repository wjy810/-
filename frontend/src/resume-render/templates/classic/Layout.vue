<script setup lang="ts">
import type { LayoutProps } from '../../components/context'
import ContactList from '../../components/primitives/ContactList.vue'
import RRegion from '../../components/RRegion.vue'

defineProps<LayoutProps>()
</script>

<template>
  <div class="rr-sheet">
    <header v-if="!page.continuation" class="cl-head">
      <h1 class="cl-name">{{ document.header.name }}</h1>
      <p v-if="document.header.title" class="cl-title">{{ document.header.title }}</p>
      <ContactList :header="document.header" :icons="false" class="cl-contact" />
    </header>
    <div v-else class="rr-continue"><strong>{{ document.header.name }}</strong><span>{{ document.header.title }}</span></div>
    <div class="rr-body"><RRegion id="main" :locale="document.locale" /></div>
  </div>
</template>

<style>
[data-template='classic'] .cl-head { margin-bottom: 4.5mm; padding-bottom: 3.6mm; border-bottom: 0.9pt solid var(--r-accent); text-align: center; }
[data-template='classic'][data-header='left'] .cl-head { text-align: left; }
[data-template='classic'] .cl-name { font-family: var(--r-font-heading); font-size: 25pt; font-weight: 600; line-height: 1.15; letter-spacing: 0.08em; color: var(--r-accent); }
[data-template='classic'] .cl-title { margin-top: 1.6mm; font-size: calc(var(--r-size) + 1pt); letter-spacing: 0.12em; color: var(--r-ink); }
[data-template='classic'] .cl-contact { justify-content: center; margin-top: 2.2mm; }
[data-template='classic'][data-header='left'] .cl-contact { justify-content: flex-start; }
[data-template='classic'] .rr-heading {
  padding-bottom: 1.2mm;
  border-bottom: 0.5pt solid var(--r-rule);
  color: var(--r-accent);
  font-size: calc(var(--r-size) + 1.5pt);
  font-weight: 600;
  letter-spacing: 0.22em;
}
[data-template='classic'] .rr-entry__title { font-family: var(--r-font-heading); }
[data-template='classic'] .rr-bullets li::before { background: var(--r-ink); width: 2.6pt; height: 2.6pt; }
</style>

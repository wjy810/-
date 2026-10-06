<script setup lang="ts">
import type { LayoutProps } from '../../components/context'
import ContactList from '../../components/primitives/ContactList.vue'
import RRegion from '../../components/RRegion.vue'

defineProps<LayoutProps>()
</script>

<template>
  <div class="rr-sheet">
    <header v-if="!page.continuation" class="en-head">
      <div>
        <h1 class="en-name">{{ document.header.name }}</h1>
        <p v-if="document.header.title" class="en-title">{{ document.header.title }}</p>
      </div>
      <ContactList :header="document.header" :icons="design.contactIcons" class="en-contact" />
    </header>
    <div v-else class="rr-continue"><strong>{{ document.header.name }}</strong><span>{{ document.header.title }}</span></div>
    <div class="rr-body"><RRegion id="main" :locale="document.locale" /></div>
  </div>
</template>

<style>
[data-template='engineer'] .en-head { display: grid; grid-template-columns: minmax(0, 1fr) auto; align-items: end; gap: 8mm; margin-bottom: 5mm; padding-bottom: 3.5mm; border-bottom: 1.2pt solid var(--r-ink); }
[data-template='engineer'][data-header='stacked'] .en-head { grid-template-columns: 1fr; gap: 3mm; }
[data-template='engineer'] .en-name { font-size: 24pt; font-weight: 700; line-height: 1.1; letter-spacing: 0.02em; }
[data-template='engineer'] .en-title { margin-top: 1.4mm; color: var(--r-accent); font-family: var(--r-font-mono); font-size: calc(var(--r-size) + 0.25pt); font-weight: 500; }
[data-template='engineer'] .en-contact { flex-direction: column; align-items: flex-end; gap: 1mm; font-family: var(--r-font-mono); font-size: calc(var(--r-size) - 1.25pt); }
[data-template='engineer'][data-header='stacked'] .en-contact { flex-direction: row; align-items: center; }
[data-template='engineer'] .en-contact .rr-contact__sep { display: none; }
[data-template='engineer'][data-header='stacked'] .en-contact .rr-contact__sep { display: inline; }
[data-template='engineer'] .rr-heading { gap: 1.6mm; color: var(--r-ink); font-size: calc(var(--r-size) + 1.25pt); }
[data-template='engineer'] .rr-heading::before { content: '//'; color: var(--r-accent); font-family: var(--r-font-mono); font-weight: 500; }
[data-template='engineer'] .rr-entry__date { font-family: var(--r-font-mono); font-size: calc(var(--r-size) - 1.25pt); }
[data-template='engineer'] .rr-entry__subtitle { color: var(--r-accent); }
[data-template='engineer'] .rr-skill { grid-template-columns: 24mm minmax(0, 1fr); }
[data-template='engineer'] .rr-skill__name { color: var(--r-muted); font-family: var(--r-font-mono); font-size: calc(var(--r-size) - 1pt); font-weight: 500; padding-top: 0.4pt; }
[data-template='engineer'] .rr-skill__name::after { content: none; }
[data-template='engineer'] .rr-skill__items { display: flex; flex-wrap: wrap; gap: 1mm 1.6mm; }
[data-template='engineer'] .rr-skill__sep { display: none; }
[data-template='engineer'] .rr-skill__item { padding: 0 1.6mm; border: 0.5pt solid var(--r-rule); border-radius: 0.8mm; font-family: var(--r-font-mono); font-size: calc(var(--r-size) - 1pt); line-height: 1.6; }
[data-template='engineer'] .rr-block--skill { padding-bottom: calc(var(--r-item-gap) * 0.6); }
[data-template='engineer'] .rr-bullets li::before { content: '›'; width: auto; height: auto; margin: 0 0 0 0.4mm; border-radius: 0; background: none; color: var(--r-accent); font-weight: 700; }
</style>

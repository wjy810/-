<script setup lang="ts">
import type { LayoutProps } from '../../components/context'
import ContactList from '../../components/primitives/ContactList.vue'
import RRegion from '../../components/RRegion.vue'

defineProps<LayoutProps>()
</script>

<template>
  <div class="rr-sheet">
    <header v-if="!page.continuation" class="ca-head">
      <h1 class="ca-name">{{ document.header.name }}</h1>
      <p v-if="document.header.title" class="ca-title">{{ document.header.title }}</p>
      <ContactList :header="document.header" :icons="design.contactIcons" separator="|" class="ca-contact" />
    </header>
    <div v-else class="rr-continue"><strong>{{ document.header.name }}</strong><span>{{ document.header.title }}</span></div>
    <div class="rr-body"><RRegion id="main" :locale="document.locale" /></div>
  </div>
</template>

<style>
[data-template='clarity'] .ca-head { margin-bottom: 5mm; }
[data-template='clarity'][data-header='centered'] .ca-head { text-align: center; }
[data-template='clarity'][data-header='centered'] .ca-contact { justify-content: center; }
[data-template='clarity'] .ca-name { font-family: var(--r-font-heading); font-size: 24pt; font-weight: 700; line-height: 1.15; color: var(--r-ink); }
[data-template='clarity'] .ca-title { margin-top: 1.4mm; color: var(--r-accent); font-size: calc(var(--r-size) + 1.25pt); font-weight: 600; }
[data-template='clarity'] .ca-contact { margin-top: 2.6mm; color: var(--r-ink); }
[data-template='clarity'] .rr-heading {
  padding-bottom: 1.8mm;
  color: var(--r-ink);
  font-size: calc(var(--r-size) + 1.5pt);
  background: linear-gradient(var(--r-accent), var(--r-accent)) no-repeat 0 100% / 9mm 2pt;
}
[data-template='clarity'] .rr-bullets li::before { background: var(--r-ink); }
</style>

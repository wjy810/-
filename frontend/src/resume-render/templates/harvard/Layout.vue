<script setup lang="ts">
import type { LayoutProps } from '../../components/context'
import ContactList from '../../components/primitives/ContactList.vue'
import RRegion from '../../components/RRegion.vue'

defineProps<LayoutProps>()
</script>

<template>
  <div class="rr-sheet">
    <header v-if="!page.continuation" class="hv-head">
      <h1 class="hv-name">{{ document.header.name }}</h1>
      <ContactList :header="document.header" :icons="false" separator="•" class="hv-contact" />
    </header>
    <div v-else class="rr-continue"><strong>{{ document.header.name }}</strong><span>{{ document.header.title }}</span></div>
    <div class="rr-body"><RRegion id="main" :locale="document.locale" /></div>
  </div>
</template>

<style>
[data-template='harvard'] .hv-head { margin-bottom: 3mm; text-align: center; }
[data-template='harvard'] .hv-name { font-family: var(--r-font-heading); font-size: 21pt; font-weight: 600; letter-spacing: 0.02em; color: var(--r-accent); }
[data-template='harvard'] .hv-contact { justify-content: center; margin-top: 1.6mm; color: var(--r-ink); }
[data-template='harvard'] .rr-heading {
  margin-bottom: 1.6mm;
  padding-bottom: 0.8mm;
  border-bottom: 0.6pt solid var(--r-accent);
  color: var(--r-accent);
  font-size: calc(var(--r-size) + 0.75pt);
  font-weight: 600;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}
/* Organisation / location on the first line, role / dates on the second. */
[data-template='harvard'] .rr-entry { grid-template-areas: 'title location' 'sub date' 'body body'; }
[data-template='harvard'] .rr-entry__meta { display: contents; }
[data-template='harvard'] .rr-entry__subtitle { grid-area: sub; font-style: italic; font-weight: 400; }
[data-template='harvard'] .rr-entry__location { grid-area: location; justify-self: end; color: var(--r-ink); font-size: var(--r-size); }
[data-template='harvard'] .rr-entry__date { color: var(--r-ink); font-style: italic; font-size: var(--r-size); }
[data-template='harvard'] .rr-entry__title { font-size: var(--r-size); font-weight: 700; }
[data-template='harvard'] .rr-bullets li::before { content: '•'; width: auto; height: auto; margin: 0 0 0 0.6mm; border-radius: 0; background: none; color: var(--r-ink); }
</style>

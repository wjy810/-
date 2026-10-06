<script setup lang="ts">
import type { LayoutProps } from '../../components/context'
import ContactList from '../../components/primitives/ContactList.vue'
import RRegion from '../../components/RRegion.vue'

defineProps<LayoutProps>()
</script>

<template>
  <div class="rr-sheet">
    <header v-if="!page.continuation" class="bk-head">
      <h1 class="bk-name">{{ document.header.name }}</h1>
      <p v-if="document.header.title" class="bk-title">{{ document.header.title }}</p>
      <ContactList :header="document.header" :icons="false" separator="|" class="bk-contact" />
    </header>
    <div v-else class="rr-continue"><strong>{{ document.header.name }}</strong></div>
    <div class="rr-body"><RRegion id="main" :locale="document.locale" /></div>
  </div>
</template>

<style>
[data-template='banker'] .bk-head { margin-bottom: 2.2mm; padding-bottom: 1.6mm; border-bottom: 2.4pt double var(--r-accent); text-align: center; }
[data-template='banker'] .bk-name { font-family: var(--r-font-heading); font-size: 18pt; line-height: 1.2; font-weight: 700; letter-spacing: 0.12em; color: var(--r-accent); }
[data-template='banker'] .bk-title { margin-top: 0.4mm; font-size: var(--r-size); letter-spacing: 0.08em; }
[data-template='banker'] .bk-contact { justify-content: center; margin-top: 0.8mm; color: var(--r-ink); }
[data-template='banker'] .rr-heading {
  margin-bottom: 0.9mm;
  padding-bottom: 0.4mm;
  border-bottom: 0.6pt solid var(--r-accent);
  color: var(--r-accent);
  font-size: calc(var(--r-size) + 0.75pt);
  font-weight: 700;
  letter-spacing: 0.2em;
}
[data-template='banker'] .rr-entry { grid-template-areas: 'title location' 'sub date' 'body body'; }
[data-template='banker'] .rr-entry__meta { display: contents; }
[data-template='banker'] .rr-entry__subtitle { grid-area: sub; font-weight: 400; font-style: italic; }
[data-template='banker'] .rr-entry__location { grid-area: location; justify-self: end; color: var(--r-ink); font-size: var(--r-size); }
[data-template='banker'] .rr-entry__date { color: var(--r-ink); font-size: var(--r-size); }
[data-template='banker'] .rr-entry__title { font-size: var(--r-size); font-weight: 700; }
[data-template='banker'] .rr-entry__body { margin-top: 0.4mm; }
[data-template='banker'] .rr-entry__title, [data-template='banker'] .rr-entry__location { line-height: var(--r-leading); }
/* Certificates, honors, languages: one dense line each. */
[data-template='banker'] .rr-list-item__detail, [data-template='banker'] .rr-list-item__date { font-size: var(--r-size); }
/* Dense rhythm: smaller section breaks and bullet gaps than the shared scale. */
.rr-root[data-template='banker'] {
  --r-bullet-gap: 0.4mm;
  --r-section-gap: calc(var(--r-size) * var(--r-leading) * 0.9 * var(--r-gap));
}
[data-template='banker'] .rr-bullets li::before { content: '–'; width: auto; height: auto; margin: 0; border-radius: 0; background: none; color: var(--r-ink); }
</style>

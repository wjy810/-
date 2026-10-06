<script setup lang="ts">
import type { LayoutProps } from '../../components/context'
import ContactList from '../../components/primitives/ContactList.vue'
import ResumePhoto from '../../components/primitives/ResumePhoto.vue'
import RRegion from '../../components/RRegion.vue'

defineProps<LayoutProps>()
</script>

<template>
  <div class="mr-page">
    <span class="rr-decor mr-side-bg" aria-hidden="true" />
    <!-- Reading order: identity → main → side; the grid puts identity and side in the left column. -->
    <header v-if="!page.continuation" class="mr-head">
      <ResumePhoto v-if="document.header.photo" :src="document.header.photo" :shape="design.photo.shape" class="mr-photo" />
      <h1 class="mr-name">{{ document.header.name }}</h1>
      <p v-if="document.header.title" class="mr-title">{{ document.header.title }}</p>
      <ContactList :header="document.header" :icons="design.contactIcons" class="mr-contact" />
    </header>
    <div v-else class="mr-head mr-head--cont"><strong>{{ document.header.name }}</strong></div>
    <RRegion id="main" :locale="document.locale" class="mr-main" />
    <RRegion id="side" :locale="document.locale" class="mr-side" />
  </div>
</template>

<style>
[data-template='meridian'] .mr-page {
  --mr-side: 62mm;
  display: grid;
  flex: 1;
  min-height: 0;
  grid-template-columns: var(--mr-side) minmax(0, 1fr);
  grid-template-rows: auto minmax(0, 1fr);
  grid-template-areas: 'head main' 'side main';
}
[data-template='meridian'] .mr-side-bg { inset: 0 auto 0 0; width: var(--mr-side); background: var(--r-accent-soft); }
[data-template='meridian'] .mr-head { grid-area: head; padding: var(--r-margin-y) 7mm 2mm var(--r-margin-x); }
[data-template='meridian'] .mr-head--cont { padding-bottom: 4mm; color: var(--r-accent); font-family: var(--r-font-heading); }
[data-template='meridian'] .mr-photo { margin-bottom: 5mm; }
[data-template='meridian'][data-header='compact'] .mr-photo { width: 20mm; height: 20mm; }
[data-template='meridian'] .mr-name { font-family: var(--r-font-heading); font-size: 21pt; font-weight: 700; line-height: 1.15; letter-spacing: 0.04em; color: var(--r-ink); }
[data-template='meridian'] .mr-title { margin-top: 1.6mm; color: var(--r-accent); font-size: calc(var(--r-size) + 0.75pt); font-weight: 600; }
[data-template='meridian'] .mr-contact { flex-direction: column; align-items: flex-start; gap: 1.8mm; margin-top: 5mm; }
[data-template='meridian'] .mr-contact .rr-contact__sep { display: none; }
[data-template='meridian'] .mr-contact .rr-contact__item { white-space: normal; word-break: break-all; }
[data-template='meridian'] .mr-side { grid-area: side; padding: 5mm 7mm var(--r-margin-y) var(--r-margin-x); }
[data-template='meridian'] .mr-main { grid-area: main; padding: var(--r-margin-y) var(--r-margin-x) var(--r-margin-y) 8mm; }
[data-template='meridian'] .rr-heading {
  padding-left: 2.6mm;
  border-left: 2.4pt solid var(--r-accent);
  color: var(--r-accent);
  font-size: calc(var(--r-size) + 1.25pt);
  line-height: 1.2;
  letter-spacing: 0.06em;
}
[data-template='meridian'] .mr-side .rr-skill { grid-template-columns: 1fr; }
[data-template='meridian'] .mr-side .rr-skill__name::after { content: none; }
[data-template='meridian'] .mr-side .rr-skill__name { margin-bottom: 1.2mm; font-size: var(--r-size-sm); color: var(--r-muted); font-weight: 500; }
[data-template='meridian'] .mr-side .rr-skill__items { display: flex; flex-wrap: wrap; gap: 1.4mm; }
[data-template='meridian'] .mr-side .rr-skill__sep { display: none; }
[data-template='meridian'] .mr-side .rr-skill__item { padding: 0.5mm 2mm; border-radius: 1mm; background: var(--r-paper); color: var(--r-ink); font-size: var(--r-size-sm); }
[data-template='meridian'] .mr-side .rr-list-item__date { margin-left: 0; }
</style>

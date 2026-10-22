<script setup lang="ts">
import type { LayoutProps } from '../../components/context'
import ContactList from '../../components/primitives/ContactList.vue'
import RRegion from '../../components/RRegion.vue'
import '../collection.css'

defineProps<LayoutProps>()
</script>

<template>

  <div class="rr-sheet collection-sheet">
    <header v-if="!page.continuation" class="collection-head">

      <div class="collection-identity">
        <h1 class="collection-name">{{ document.header.name }}</h1>
        <p v-if="document.header.title" class="collection-title">{{ document.header.title }}</p>
      </div>

      <ContactList :header="document.header" :icons="design.contactIcons" class="collection-contact" />
    </header>
    <div v-else class="rr-continue"><strong>{{ document.header.name }}</strong><span>{{ document.header.title }}</span></div>
    <div class="rr-body collection-body">
      <RRegion id="main" :locale="document.locale" class="collection-main" />

    </div>
  </div>
</template>

<style>
[data-template='consultant'] .collection-head { border-bottom: 1.3pt solid var(--r-ink); margin-bottom: 5mm; padding-bottom: 4mm; grid-template-columns: 1fr; }
[data-template='consultant'] .collection-identity { display: flex; justify-content: space-between; align-items: baseline; flex-wrap: wrap; gap: 2mm 5mm; }
[data-template='consultant'] .collection-name { font-size: 30pt; font-weight: 600; letter-spacing: -.02em; }
[data-template='consultant'] .collection-title { font-family: var(--r-font-body); font-size: 10pt; margin: 0; }
[data-template='consultant'] .collection-contact { font-size: 8pt; }
[data-template='consultant'] .rr-heading { text-transform: uppercase; letter-spacing: .1em; font-size: 9pt; color: var(--r-ink); border-bottom: .5pt solid var(--r-rule); padding-bottom: 1.5mm; }
[data-template='consultant'] .rr-entry { grid-template-areas: 'title title' 'meta date' 'body body'; column-gap: 4mm; }
[data-template='consultant'] .rr-entry__title { font-weight: 600; }
[data-template='consultant'] .rr-entry__meta { margin-top: .5mm; }
[data-template='consultant'] .rr-entry__date { margin-top: .5mm; }
[data-template='consultant'] .rr-entry__subtitle { font-style: italic; }
</style>

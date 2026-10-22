<script setup lang="ts">
import type { LayoutProps } from '../../components/context'
import ContactList from '../../components/primitives/ContactList.vue'
import ResumePhoto from '../../components/primitives/ResumePhoto.vue'
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
      <ResumePhoto v-if="document.header.photo" :src="document.header.photo" :shape="design.photo.shape" class="collection-photo" />
      <ContactList :header="document.header" :icons="design.contactIcons" class="collection-contact" />
    </header>
    <div v-else class="rr-continue"><strong>{{ document.header.name }}</strong><span>{{ document.header.title }}</span></div>
    <div class="rr-body collection-body">
      <RRegion id="main" :locale="document.locale" class="collection-main" />
      <RRegion id="side" :locale="document.locale" class="collection-side" />
    </div>
  </div>
</template>

<style>
[data-template='folio'] .collection-sheet { --c-side-width: 58mm; }
[data-template='folio'] .collection-head { padding: 5mm 0; border-top: .7pt solid var(--r-accent); border-bottom: .7pt solid var(--r-accent); }
[data-template='folio'] .collection-name { font-size: 34pt; font-weight: 500; }
[data-template='folio'] .collection-title { font-size: 10.5pt; }
[data-template='folio'] .collection-head:not(:has(.rr-photo)) .collection-contact { grid-column: 2; grid-row: 1; width: 66mm; justify-content: flex-end; text-align: right; font-size: 8pt; }
[data-template='folio'] .collection-body { gap: 0; }
[data-template='folio'] .collection-main { padding-right: 7mm; }
[data-template='folio'] .collection-side { border-left: .5pt solid var(--r-rule); padding-left: 6mm; }
[data-template='folio'] .rr-heading { font-family: var(--r-font-heading); border-bottom: .5pt solid var(--r-rule); padding-bottom: 1.8mm; letter-spacing: .08em; font-weight: 500; }
[data-template='folio'] .collection-side .rr-heading { color: var(--r-ink); }
</style>

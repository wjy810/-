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
[data-template='clinical'] .collection-sheet { --c-side-width: 54mm; }
[data-template='clinical'] .collection-head { border-top: 2pt solid var(--r-accent); padding-top: 5mm; border-bottom: .5pt solid var(--r-rule); }
[data-template='clinical'] .collection-name { font-size: 30pt; font-weight: 500; letter-spacing: .1em; }
[data-template='clinical'] .collection-photo { border: .5pt solid var(--r-rule); }
[data-template='clinical'] .collection-side { padding: 0 0 0 5mm; border-left: .5pt solid var(--r-rule); background: none; }
[data-template='clinical'] .rr-heading { color: var(--r-accent); padding-bottom: 1.6mm; border-bottom: .5pt solid var(--r-rule); font-weight: 600; }
[data-template='clinical'] .collection-side .rr-heading { font-size: var(--r-size); border-bottom-color: var(--r-accent); }
[data-template='clinical'] .rr-entry__subtitle { font-weight: 500; }
</style>

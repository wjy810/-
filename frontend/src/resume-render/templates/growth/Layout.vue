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

    </div>
  </div>
</template>

<style>
[data-template='growth'] .collection-head { border-left: 2pt solid var(--r-accent); padding-left: 5mm; padding-top: 2mm; }
[data-template='growth'] .collection-name { font-size: 33pt; font-weight: 600; letter-spacing: -.02em; }
[data-template='growth'] .rr-heading { gap: 3mm; border-bottom: .5pt solid var(--r-rule); padding-bottom: 1.8mm; font-weight: 600; }
[data-template='growth'] .rr-heading__index { display: inline; color: var(--r-accent); font-size: 15pt; font-variant-numeric: tabular-nums; font-weight: 400; font-family: var(--r-font-mono); }
[data-template='growth'] .rr-skill { padding: 1mm 0; border-bottom: .5pt solid var(--r-rule); }
[data-template='growth'] .rr-bullets strong { color: var(--r-accent); }
[data-template='growth'] .rr-entry__title { font-weight: 600; }
</style>

<script setup lang="ts">
import type { LayoutProps } from '../../components/context'
import ContactList from '../../components/primitives/ContactList.vue'
import ResumePhoto from '../../components/primitives/ResumePhoto.vue'
import RRegion from '../../components/RRegion.vue'
import { artUrl } from '../../assets/art'
import '../collection.css'

defineProps<LayoutProps>()
</script>

<template>
  <img v-if="design.decorations && theme.palette.art" class="rr-decor collection-art" :src="artUrl(theme.palette.art)" alt="" aria-hidden="true" />
  <span class="rr-decor mosaic-square" aria-hidden="true" />
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
[data-template='mosaic'] .collection-sheet { --c-side-width: 52mm; }
[data-template='mosaic'] .collection-art { top: -5mm; right: -2mm; width: 37mm; height: 38mm; opacity: .5; }
[data-template='mosaic'] .mosaic-square { display: none; }
[data-template='mosaic'] .collection-head { padding: 2mm 0 4mm 5mm; border-left: 7mm solid var(--r-accent); border-bottom: 0; }
[data-template='mosaic'] .collection-name { font-size: 37pt; font-weight: 700; letter-spacing: -.035em; }
[data-template='mosaic'] .collection-title { font-weight: 500; margin-top: 3mm; }
[data-template='mosaic'] .collection-side { border-left: 0; padding: 0 0 0 4mm; }
[data-template='mosaic'] .collection-side .rr-block { padding-left: 3mm; border-left: .7pt solid var(--r-rule); }
[data-template='mosaic'] .collection-side .rr-block--start { border-left-color: var(--r-accent); }
[data-template='mosaic'] .rr-heading { font-weight: 600; gap: 2.5mm; border-top: 1pt solid var(--r-ink); padding-top: 2mm; }
[data-template='mosaic'] .rr-heading__index { display: inline; font-size: 8pt; color: var(--r-accent); font-family: var(--r-font-mono); }
[data-template='mosaic'] .collection-side .rr-heading { border-top: 0; padding-top: 0; }
</style>

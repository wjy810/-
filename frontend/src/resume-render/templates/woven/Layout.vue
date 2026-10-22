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
[data-template='woven'] .collection-sheet { padding-left: calc(var(--r-margin-x) + 3mm); }
[data-template='woven'] .collection-art { top: 0; bottom: 0; left: 0; width: 7mm; height: 100%; object-fit: cover; opacity: .6; border-right: .5pt solid var(--r-rule); }
[data-template='woven'] .collection-head { border-top: .5pt solid var(--r-rule); border-bottom: 1pt solid var(--r-accent); padding-top: 5mm; }
[data-template='woven'] .collection-name { font-size: 33pt; letter-spacing: .12em; font-weight: 500; }
[data-template='woven'] .collection-title { letter-spacing: .1em; }
[data-template='woven'] .rr-heading { letter-spacing: .14em; border-bottom: .5pt solid var(--r-rule); padding-bottom: 1.5mm; font-weight: 500; }
[data-template='woven'] .rr-heading__index { display: inline; color: var(--r-muted); font-size: 8pt; font-family: var(--r-font-mono); margin-right: 2mm; }
[data-template='woven'] .rr-entry__title { font-family: var(--r-font-heading); }
</style>

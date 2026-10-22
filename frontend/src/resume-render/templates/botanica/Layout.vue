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
      <RRegion id="side" :locale="document.locale" class="collection-side" />
    </div>
  </div>
</template>

<style>
[data-template='botanica'] .collection-sheet { --c-side-width: 49mm; }
[data-template='botanica'] .collection-art { top: 5mm; right: -5mm; width: 30mm; height: 49mm; opacity: .42; }
[data-template='botanica'] .collection-head { border-bottom: .6pt solid var(--r-accent); padding-right: 12mm; padding-top: 2mm; }
[data-template='botanica'] .collection-name { font-size: 32pt; font-weight: 500; letter-spacing: .12em; }
[data-template='botanica'] .collection-title { letter-spacing: .08em; }
[data-template='botanica'] .collection-side { background: none; border-left: .5pt solid var(--r-rule); padding: 0 0 0 5mm; }
[data-template='botanica'] .rr-heading { letter-spacing: .1em; gap: 3mm; }
[data-template='botanica'] .rr-heading::after { content: ''; width: 9mm; height: .7pt; background: var(--r-accent); align-self: center; }
[data-template='botanica'] .collection-side .rr-heading { font-size: var(--r-size); padding-bottom: 2mm; border-bottom: .5pt solid var(--r-rule); }
[data-template='botanica'] .collection-side .rr-heading::after { content: none; }
</style>

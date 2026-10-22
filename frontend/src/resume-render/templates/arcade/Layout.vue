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
[data-template='arcade'] .collection-art { inset: 0; width: 100%; height: 100%; object-fit: cover; opacity: .45; }
[data-template='arcade'] .collection-head { border: .7pt solid var(--r-accent); border-bottom-width: 1.5pt; border-radius: 18mm 18mm 0 0; padding: 9mm 7mm 5mm; text-align: center; }
[data-template='arcade'] .collection-name { font-size: 32pt; font-weight: 500; letter-spacing: .15em; }
[data-template='arcade'] .collection-title { font-family: var(--r-font-heading); letter-spacing: .1em; }
[data-template='arcade'] .collection-contact { justify-content: center; padding-top: 2mm; }
[data-template='arcade'] .rr-heading { gap: 4mm; letter-spacing: .12em; font-weight: 500; }
[data-template='arcade'] .rr-heading::after { content: ''; height: .5pt; flex: 1; background: var(--r-rule); align-self: center; }
[data-template='arcade'] .rr-entry__title { font-family: var(--r-font-heading); }
[data-template='arcade'][data-header='compact'] .collection-head { border-radius: 8mm 8mm 0 0; padding: 5mm 6mm 4mm; }
</style>

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
[data-template='porcelain'] .collection-art { right: 19mm; top: 16mm; width: 22mm; height: 34mm; opacity: .5; }
[data-template='porcelain'] .collection-head { padding: 5mm 29mm 5mm 6mm; border: .5pt solid var(--r-rule); border-left: 2pt solid var(--r-accent); margin-bottom: 7mm; }
[data-template='porcelain'] .collection-name { font-size: 30pt; letter-spacing: .22em; font-weight: 500; }
[data-template='porcelain'] .collection-title { font-family: var(--r-font-heading); letter-spacing: .12em; }
[data-template='porcelain'] .collection-contact { padding-top: 2mm; }
[data-template='porcelain'] .rr-heading { letter-spacing: .16em; gap: 3mm; font-weight: 500; }
[data-template='porcelain'] .rr-heading::after { content: ''; height: .5pt; flex: 1; background: var(--r-rule); align-self: center; }
[data-template='porcelain'] .rr-entry__title { font-family: var(--r-font-heading); }
[data-template='porcelain'] .rr-page--continuation .collection-art { top: auto; bottom: 5mm; right: 2mm; width: 12mm; height: 22mm; opacity: .25; }
</style>

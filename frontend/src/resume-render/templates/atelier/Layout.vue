<script setup lang="ts">
import type { LayoutProps } from '../../components/context'
import ContactList from '../../components/primitives/ContactList.vue'
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
[data-template='atelier'] .collection-sheet { --c-side-width: 44mm; }
[data-template='atelier'] .collection-art { inset: 0; width: 100%; height: 100%; object-fit: cover; opacity: .5; }
[data-template='atelier'] .collection-head { padding-top: 1mm; padding-bottom: 6mm; border-bottom: 1pt solid var(--r-ink); }
[data-template='atelier'] .collection-name { font-size: 45pt; font-weight: 400; letter-spacing: -.045em; }
[data-template='atelier'] .collection-title { font-family: var(--r-font-heading); font-size: 13pt; font-style: italic; margin-top: 3mm; }
[data-template='atelier'] .collection-contact { margin-top: 2mm; font-size: 8pt; }
[data-template='atelier'] .collection-side { padding-left: 0; border-left: 0; }
[data-template='atelier'] .rr-heading { text-transform: uppercase; letter-spacing: .16em; font-size: 8pt; border-top: .5pt solid var(--r-rule); padding-top: 2.5mm; color: var(--r-ink); }
[data-template='atelier'] .rr-heading__index { display: inline; font-size: 7pt; color: var(--r-muted); }
[data-template='atelier'] .rr-entry__title { font-family: var(--r-font-heading); font-size: calc(var(--r-size-title) + 1pt); font-weight: 500; }
[data-template='atelier'] .collection-side .rr-heading { font-size: 8pt; }
</style>

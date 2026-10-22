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
[data-template='shoreline'] .collection-art { bottom: 3mm; left: 0; width: 100%; height: 8.5mm; object-fit: fill; opacity: .55; }
[data-template='shoreline'] .collection-head { border-bottom: .5pt solid var(--r-rule); padding-bottom: 5mm; }
[data-template='shoreline'] .collection-name { font-size: 33pt; font-weight: 500; letter-spacing: .08em; }
[data-template='shoreline'] .collection-title { letter-spacing: .06em; }
[data-template='shoreline'] .collection-contact { padding-top: 1.5mm; }
[data-template='shoreline'] .rr-heading { gap: 3mm; letter-spacing: .05em; }
[data-template='shoreline'] .rr-heading::after { content: ''; width: 10mm; height: 1pt; background: var(--r-accent); align-self: center; }
[data-template='shoreline'] .rr-entry__subtitle { color: var(--r-accent); }
[data-template='shoreline'] .rr-bullets li::before { border-radius: 0; width: 4pt; height: 1pt; margin-top: calc(var(--r-size) * var(--r-leading) * .5); }
</style>

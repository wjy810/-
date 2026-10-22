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
[data-template='axis'] .collection-head { padding-left: 27mm; border-bottom: 1pt solid var(--r-ink); }
[data-template='axis'] .collection-name { font-size: 32pt; font-weight: 500; letter-spacing: .04em; }
[data-template='axis'] .collection-contact { font-size: 8pt; }
[data-template='axis'] .rr-block { display: grid; grid-template-columns: 24mm minmax(0, 1fr); column-gap: 3mm; }
[data-template='axis'] .rr-block > :not(.rr-heading) { grid-column: 2; min-width: 0; }
[data-template='axis'] .rr-heading { grid-column: 1; grid-row: 1; align-items: flex-start; font-size: 8.5pt; line-height: 1.5; padding-top: .3mm; padding-right: 3mm; border-right: .5pt solid var(--r-rule); letter-spacing: .1em; font-weight: 500; }
[data-template='axis'] .rr-heading + * { grid-row: 1; }
[data-template='axis'] .rr-entry__title { font-weight: 600; }
</style>

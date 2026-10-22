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
[data-template='product'] .collection-sheet { --c-side-width: 47mm; }
[data-template='product'] .collection-head { border-bottom: 1.5pt solid var(--r-accent); }
[data-template='product'] .collection-name { font-size: 31pt; font-weight: 600; letter-spacing: .02em; }
[data-template='product'] .collection-title { font-weight: 500; }
[data-template='product'] .collection-side { border-left: 0; border-top: .5pt solid var(--r-rule); padding: 3mm 0 0 4mm; background: none; }
[data-template='product'] .rr-heading { font-size: 10pt; letter-spacing: .06em; }
[data-template='product'] .collection-main .rr-block--timeline { border-left: .5pt solid var(--r-rule); padding-left: 4mm; }
[data-template='product'] .collection-main .rr-block--timeline .rr-heading { margin-left: -4mm; padding-left: 3.3mm; border-left: 2pt solid var(--r-accent); }
[data-template='product'] .rr-entry__subtitle { color: var(--r-accent); font-weight: 500; }
[data-template='product'] .collection-side .rr-heading { font-size: 9pt; }
</style>

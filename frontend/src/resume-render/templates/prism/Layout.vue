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
[data-template='prism'] .collection-sheet { --c-side-width: 49mm; }
[data-template='prism'] .collection-head { padding: 5mm 6mm; background: linear-gradient(135deg, var(--r-accent-soft) 0 90%, var(--r-paper) 90%); border-left: 2.5pt solid var(--r-accent); }
[data-template='prism'] .collection-name { font-size: 32pt; font-weight: 700; letter-spacing: -.02em; }
[data-template='prism'] .collection-side { border-left: 0; border-top: 2.5pt solid var(--r-accent); padding: 3mm 0 0 3mm; }
[data-template='prism'] .rr-heading { border-left: 2pt solid var(--r-accent); padding-left: 2mm; font-weight: 600; }
[data-template='prism'] .rr-entry__subtitle { color: var(--r-accent); }
[data-template='prism'] .collection-contact { font-size: 8pt; }
</style>

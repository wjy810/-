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
      <span class="collection-monogram" aria-hidden="true">{{ document.header.initial }}</span>
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
[data-template='orbit'] .collection-head { grid-template-columns: auto minmax(0, 1fr) auto; column-gap: 4mm; border-bottom: .7pt solid var(--r-rule); }
[data-template='orbit'] .collection-monogram { display: grid; place-items: center; width: 18mm; height: 18mm; border: .7pt solid var(--r-accent); border-radius: 50%; color: var(--r-accent); font-family: var(--r-font-heading); font-size: 22pt; line-height: 1; }
[data-template='orbit'] .collection-name { font-size: 30pt; font-weight: 500; }
[data-template='orbit'] .collection-contact { grid-column: 1 / -1; margin-top: 2mm; }
[data-template='orbit'] .rr-heading { gap: 2.5mm; font-weight: 600; }
[data-template='orbit'] .rr-heading::before { content: ''; width: 1.3mm; height: 1.3mm; border-radius: 50%; background: var(--r-accent); flex: none; align-self: center; }
[data-template='orbit'] .rr-heading::after { content: ''; height: .5pt; flex: 1; background: var(--r-rule); align-self: center; }
</style>

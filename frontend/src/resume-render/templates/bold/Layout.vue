<script setup lang="ts">
import type { LayoutProps } from '../../components/context'
import ContactList from '../../components/primitives/ContactList.vue'
import ResumePhoto from '../../components/primitives/ResumePhoto.vue'
import RRegion from '../../components/RRegion.vue'

defineProps<LayoutProps>()
</script>

<template>
  <span v-if="!page.continuation" class="rr-decor rr-decor--always bd-band" aria-hidden="true" />
  <div class="bd-page">
    <header v-if="!page.continuation" class="bd-head">
      <div class="bd-identity">
        <h1 class="bd-name">{{ document.header.name }}</h1>
        <p v-if="document.header.title" class="bd-title">{{ document.header.title }}</p>
        <ContactList :header="document.header" :icons="design.contactIcons" class="bd-contact" />
      </div>
      <ResumePhoto v-if="document.header.photo" :src="document.header.photo" :shape="design.photo.shape" class="bd-photo" />
    </header>
    <div v-else class="rr-continue bd-continue"><strong>{{ document.header.name }}</strong><span>{{ document.header.title }}</span></div>
    <div class="rr-body bd-body"><RRegion id="main" :locale="document.locale" /></div>
  </div>
</template>

<style>
[data-template='bold'] { --bd-band: 44mm; }
[data-template='bold'][data-header='tall'] { --bd-band: 54mm; }
[data-template='bold'] .bd-band { inset: 0 0 auto 0; height: var(--bd-band); background: var(--r-accent); }
[data-template='bold'] .bd-page { display: flex; flex: 1; flex-direction: column; min-height: 0; }
[data-template='bold'] .bd-head { display: flex; align-items: center; justify-content: space-between; gap: 8mm; height: var(--bd-band); padding: 0 var(--r-margin-x); color: #fff; }
[data-template='bold'] .bd-name { font-family: var(--r-font-heading); font-size: 27pt; font-weight: 700; line-height: 1.1; letter-spacing: 0.04em; }
[data-template='bold'] .bd-title { margin-top: 1.8mm; font-size: calc(var(--r-size) + 1.5pt); font-weight: 500; opacity: 0.92; }
[data-template='bold'] .bd-contact { margin-top: 3.2mm; color: rgb(255 255 255 / 0.86); }
[data-template='bold'] .bd-contact .rr-contact__icon { stroke: #fff; }
[data-template='bold'] .bd-contact .rr-contact__sep { color: rgb(255 255 255 / 0.5); }
[data-template='bold'] .bd-photo { flex: none; width: 27mm; height: 33mm; outline: 1.2mm solid rgb(255 255 255 / 0.9); }
[data-template='bold'] .bd-photo[data-shape='circle'] { width: 30mm; height: 30mm; }
[data-template='bold'] .bd-continue { margin: var(--r-margin-y) var(--r-margin-x) 5mm; }
[data-template='bold'] .bd-body { padding: 7mm var(--r-margin-x) var(--r-margin-y); }
[data-template='bold'] .rr-page--continuation .bd-body { padding-top: 0; }
[data-template='bold'] .rr-heading {
  padding-bottom: 1.2mm;
  border-bottom: 1.5pt solid var(--r-accent);
  color: var(--r-accent);
  font-size: calc(var(--r-size) + 1.75pt);
  letter-spacing: 0.06em;
}
[data-template='bold'] .rr-entry__subtitle { color: var(--r-accent); }
</style>

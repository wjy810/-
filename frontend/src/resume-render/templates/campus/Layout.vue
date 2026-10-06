<script setup lang="ts">
import type { LayoutProps } from '../../components/context'
import ContactList from '../../components/primitives/ContactList.vue'
import ResumePhoto from '../../components/primitives/ResumePhoto.vue'
import RRegion from '../../components/RRegion.vue'

defineProps<LayoutProps>()
</script>

<template>
  <div class="rr-sheet">
    <header v-if="!page.continuation" class="cp-head">
      <div class="cp-identity">
        <h1 class="cp-name">{{ document.header.name }}</h1>
        <p v-if="document.header.title" class="cp-title"><span>求职意向</span>{{ document.header.title }}</p>
        <ContactList :header="document.header" :icons="design.contactIcons" class="cp-contact" />
      </div>
      <ResumePhoto v-if="document.header.photo && design.headerVariant !== 'plain'" :src="document.header.photo" :shape="design.photo.shape" class="cp-photo" />
    </header>
    <div v-else class="rr-continue"><strong>{{ document.header.name }}</strong><span>{{ document.header.title }}</span></div>
    <div class="rr-body"><RRegion id="main" :locale="document.locale" /></div>
  </div>
</template>

<style>
[data-template='campus'] .cp-head { display: flex; align-items: center; justify-content: space-between; gap: 8mm; margin-bottom: 5mm; }
[data-template='campus'] .cp-name { font-size: 25pt; font-weight: 700; line-height: 1.1; letter-spacing: 0.04em; }
[data-template='campus'] .cp-title { display: flex; align-items: center; gap: 2mm; margin-top: 2mm; color: var(--r-ink); font-size: calc(var(--r-size) + 1pt); font-weight: 600; }
[data-template='campus'] .cp-title span { padding: 0.3mm 1.8mm; border-radius: 1mm; background: var(--r-accent); color: var(--r-on-accent); font-size: calc(var(--r-size) - 1.5pt); font-weight: 500; letter-spacing: 0.06em; }
[data-template='campus'] .cp-contact { margin-top: 2.6mm; }
[data-template='campus'] .rr-heading {
  padding: 0.9mm 2.4mm;
  border-radius: 1mm;
  background: var(--r-accent-soft);
  color: var(--r-accent);
  font-size: calc(var(--r-size) + 1.25pt);
  line-height: 1.3;
  letter-spacing: 0.06em;
}
[data-template='campus'] .rr-entry, [data-template='campus'] .rr-summary, [data-template='campus'] .rr-skill, [data-template='campus'] .rr-list-item { padding-inline: 2.4mm; }
[data-template='campus'] .rr-skill__items { display: flex; flex-wrap: wrap; gap: 1.2mm 1.6mm; }
[data-template='campus'] .rr-skill__sep { display: none; }
[data-template='campus'] .rr-skill__item { padding: 0 2mm; border-radius: 999px; background: var(--r-accent-soft); color: var(--r-ink); font-size: calc(var(--r-size) - 0.75pt); line-height: 1.75; }
[data-template='campus'] .rr-block--list[data-section='honors'] .rr-list-item__title::before { content: '★ '; color: var(--r-accent); }
</style>

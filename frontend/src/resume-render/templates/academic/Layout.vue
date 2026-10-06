<script setup lang="ts">
import type { LayoutProps } from '../../components/context'
import ContactList from '../../components/primitives/ContactList.vue'
import ResumePhoto from '../../components/primitives/ResumePhoto.vue'
import RRegion from '../../components/RRegion.vue'

defineProps<LayoutProps>()
</script>

<template>
  <div class="rr-sheet">
    <header v-if="!page.continuation" class="ac-head">
      <div class="ac-identity">
        <h1 class="ac-name">{{ document.header.name }}</h1>
        <p v-if="document.header.title" class="ac-title">{{ document.header.title }}</p>
        <ContactList :header="document.header" :icons="design.contactIcons" class="ac-contact" />
      </div>
      <ResumePhoto v-if="document.header.photo" :src="document.header.photo" :shape="design.photo.shape" class="ac-photo" />
    </header>
    <div v-else class="rr-continue"><strong>{{ document.header.name }}</strong><span>Curriculum Vitae</span></div>
    <div class="rr-body"><RRegion id="main" :locale="document.locale" /></div>
  </div>
</template>

<style>
[data-template='academic'] .ac-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 8mm; margin-bottom: 6mm; }
[data-template='academic'][data-header='centered'] .ac-head { justify-content: center; text-align: center; }
[data-template='academic'][data-header='centered'] .ac-contact { justify-content: center; }
[data-template='academic'] .ac-name { font-family: var(--r-font-heading); font-size: 24pt; font-weight: 600; letter-spacing: 0.06em; color: var(--r-accent); }
[data-template='academic'] .ac-title { margin-top: 1.4mm; font-size: calc(var(--r-size) + 1pt); font-style: italic; }
[data-template='academic'] .ac-contact { margin-top: 2.6mm; }
[data-template='academic'] .rr-heading {
  padding-left: 2.4mm;
  border-left: 1.2pt solid var(--r-accent);
  color: var(--r-accent);
  font-size: calc(var(--r-size) + 1.25pt);
  font-weight: 600;
  letter-spacing: 0.1em;
  line-height: 1.15;
}
[data-template='academic'] .rr-entry__title { font-family: var(--r-font-heading); }
[data-template='academic'] .rr-entry__subtitle { font-style: italic; font-weight: 400; }
[data-template='academic'] .rr-bullets li::before { background: var(--r-ink); width: 2.4pt; height: 2.4pt; }
</style>

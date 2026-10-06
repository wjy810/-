<script setup lang="ts">
import type { LayoutProps } from '../../components/context'
import ContactList from '../../components/primitives/ContactList.vue'
import ResumePhoto from '../../components/primitives/ResumePhoto.vue'
import RRegion from '../../components/RRegion.vue'

defineProps<LayoutProps>()
</script>

<template>
  <div class="rr-sheet">
    <header v-if="!page.continuation" class="lg-head">
      <ResumePhoto v-if="document.header.photo && design.headerVariant === 'photo'" :src="document.header.photo" :shape="design.photo.shape" class="lg-photo" />
      <div class="lg-identity">
        <h1 class="lg-name">{{ document.header.name }}</h1>
        <p v-if="document.header.title" class="lg-title">{{ document.header.title }}</p>
      </div>
      <ContactList :header="document.header" :icons="design.contactIcons" class="lg-contact" />
    </header>
    <div v-else class="rr-continue"><strong>{{ document.header.name }}</strong><span>{{ document.header.title }}</span></div>
    <div class="rr-body lg-body">
      <RRegion id="main" :locale="document.locale" class="lg-main" />
      <RRegion id="facts" :locale="document.locale" class="lg-facts" />
    </div>
  </div>
</template>

<style>
[data-template='ledger'] .lg-head { display: flex; align-items: center; gap: 6mm; margin-bottom: 6mm; padding-bottom: 4mm; border-bottom: 0.5pt solid var(--r-rule); }
[data-template='ledger'] .lg-photo { width: 22mm; height: 22mm; }
[data-template='ledger'] .lg-identity { flex: 1; min-width: 0; }
[data-template='ledger'] .lg-name { font-family: var(--r-font-heading); font-size: 25pt; font-weight: 700; line-height: 1.1; letter-spacing: 0.02em; }
[data-template='ledger'] .lg-title { margin-top: 1.6mm; color: var(--r-accent); font-size: calc(var(--r-size) + 1pt); font-weight: 600; }
[data-template='ledger'] .lg-contact { flex-direction: column; align-items: flex-end; gap: 1.1mm; text-align: right; }
[data-template='ledger'] .lg-contact .rr-contact__sep { display: none; }
[data-template='ledger'] .lg-contact .rr-contact__item { flex-direction: row-reverse; }
[data-template='ledger'] .lg-body { gap: 0; }
[data-template='ledger'] .lg-main { flex: 1 1 0; padding-right: 7mm; }
[data-template='ledger'] .lg-facts { flex: 0 0 58mm; padding-left: 6mm; border-left: 0.5pt solid var(--r-rule); }
[data-template='ledger'] .rr-heading { gap: 2mm; font-size: calc(var(--r-size) + 0.75pt); letter-spacing: 0.16em; color: var(--r-ink); }
[data-template='ledger'] .rr-heading::before { content: ''; align-self: center; flex: none; width: 5pt; height: 5pt; background: var(--r-accent); }
[data-template='ledger'] .lg-facts .rr-entry { grid-template-areas: 'title title' 'meta meta' 'date date' 'body body'; }
[data-template='ledger'] .lg-facts .rr-entry__date { margin-top: 0.4mm; }
[data-template='ledger'] .lg-facts .rr-entry__meta { flex-direction: column; }
[data-template='ledger'] .lg-facts .rr-skill { grid-template-columns: 1fr; }
[data-template='ledger'] .lg-facts .rr-skill__name { color: var(--r-muted); font-size: var(--r-size-sm); font-weight: 500; }
[data-template='ledger'] .lg-facts .rr-skill__name::after { content: none; }
[data-template='ledger'] .lg-facts .rr-list-item__date { margin-left: 0; }
</style>

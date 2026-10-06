<script setup lang="ts">
import { computed } from 'vue'
import type { LayoutProps } from '../../components/context'
import ContactList from '../../components/primitives/ContactList.vue'
import ResumePhoto from '../../components/primitives/ResumePhoto.vue'
import RRegion from '../../components/RRegion.vue'
import { artUrl } from '../../assets/art'

const props = defineProps<LayoutProps>()
const wash = computed(() => ({ backgroundImage: `url(${artUrl(props.theme.palette.art ?? 'wash-indigo')})` }))
</script>

<template>
  <span v-if="!page.continuation" class="rr-decor wc-wash" :style="wash" aria-hidden="true" />
  <div class="rr-sheet">
    <header v-if="!page.continuation" class="wc-head">
      <ResumePhoto v-if="document.header.photo && design.headerVariant === 'photo'" :src="document.header.photo" :shape="design.photo.shape" class="wc-photo" />
      <h1 class="wc-name">{{ document.header.name }}</h1>
      <p v-if="document.header.title" class="wc-title">{{ document.header.title }}</p>
      <ContactList :header="document.header" :icons="design.contactIcons" class="wc-contact" />
    </header>
    <div v-else class="rr-continue"><strong>{{ document.header.name }}</strong><span>{{ document.header.title }}</span></div>
    <div class="rr-body"><RRegion id="main" :locale="document.locale" /></div>
  </div>
</template>

<style>
[data-template='watercolor'] .wc-wash { top: -30mm; left: -4mm; right: -4mm; height: 100mm; background: no-repeat center top / 100% auto; opacity: 0.4; }
[data-template='watercolor'] .wc-head { display: flex; flex-direction: column; align-items: center; margin: 10mm 0 9mm; text-align: center; }
[data-template='watercolor'] .wc-photo { margin-bottom: 4mm; outline: 1.2mm solid rgb(255 255 255 / 0.85); }
[data-template='watercolor'] .wc-name { font-family: var(--r-font-heading); font-size: 30pt; font-weight: 700; line-height: 1.1; letter-spacing: 0.14em; color: var(--r-ink); }
[data-template='watercolor'] .wc-title { margin-top: 2.2mm; color: var(--r-accent); font-size: calc(var(--r-size) + 1.75pt); font-weight: 600; letter-spacing: 0.12em; }
[data-template='watercolor'] .wc-contact { justify-content: center; margin-top: 3.4mm; color: var(--r-ink); }
[data-template='watercolor'] .rr-heading {
  justify-content: center;
  gap: 3mm;
  color: var(--r-accent);
  font-size: calc(var(--r-size) + 1.75pt);
  letter-spacing: 0.2em;
}
[data-template='watercolor'] .rr-heading::before,
[data-template='watercolor'] .rr-heading::after { content: ''; align-self: center; flex: 1; height: 0.5pt; background: color-mix(in srgb, var(--r-accent) 40%, transparent); }
[data-template='watercolor'] .rr-entry__subtitle { color: var(--r-accent); }
</style>

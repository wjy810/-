<script setup lang="ts">
import { computed } from 'vue'
import type { LayoutProps } from '../../components/context'
import ContactList from '../../components/primitives/ContactList.vue'
import ResumePhoto from '../../components/primitives/ResumePhoto.vue'
import RRegion from '../../components/RRegion.vue'
import { artUrl } from '../../assets/art'

const props = defineProps<LayoutProps>()
const corner = computed(() => props.theme.palette.art ?? 'ink-plum')
const cornerStyle = computed(() => ({ backgroundImage: `url(${artUrl(corner.value)})` }))
const mountains = { backgroundImage: `url(${artUrl('ink-mountains')})` }
const stroke = { '--iw-stroke': `url(${artUrl('ink-wash-band')})` }
</script>

<template>
  <span class="rr-decor iw-corner" :class="`iw-corner--${corner}`" :style="cornerStyle" aria-hidden="true" />
  <span class="rr-decor iw-mountains" :style="mountains" aria-hidden="true" />
  <div class="rr-sheet iw-sheet" :style="stroke">
    <header v-if="!page.continuation" class="iw-head">
      <ResumePhoto v-if="document.header.photo" :src="document.header.photo" :shape="design.photo.shape" class="iw-photo" />
      <div class="iw-identity">
        <h1 class="iw-name">
          {{ document.header.name }}
          <span v-if="design.headerVariant === 'seal' && document.header.initial" class="iw-seal" aria-hidden="true">{{ document.header.initial }}</span>
        </h1>
        <p v-if="document.header.title" class="iw-title">{{ document.header.title }}</p>
        <ContactList :header="document.header" :icons="false" separator="·" class="iw-contact" />
      </div>
    </header>
    <div v-else class="rr-continue"><strong>{{ document.header.name }}</strong><span>{{ document.header.title }}</span></div>
    <div class="rr-body"><RRegion id="main" :locale="document.locale" /></div>
  </div>
</template>

<style>
[data-template='inkwash'] .iw-corner { top: -4mm; right: -2mm; width: 58mm; height: 46mm; background: no-repeat right top / contain; opacity: 0.5; }
[data-template='inkwash'] .iw-corner--ink-bamboo { top: 0; width: 26mm; height: 88mm; opacity: 0.22; }
[data-template='inkwash'] .iw-mountains { left: 0; right: 0; bottom: 0; height: 34mm; background: no-repeat center bottom / 100% auto; opacity: 0.22; }
[data-template='inkwash'] .iw-sheet { padding-bottom: calc(var(--r-margin-y) + 16mm); }
[data-template='inkwash'] .iw-identity { max-width: 132mm; }
[data-template='inkwash'] .iw-head { display: flex; align-items: center; gap: 7mm; margin: 2mm 0 7mm; }
[data-template='inkwash'] .iw-name { display: flex; align-items: center; gap: 3.2mm; font-family: var(--r-font-heading); font-size: 29pt; font-weight: 600; line-height: 1.1; letter-spacing: 0.18em; color: var(--r-ink); }
[data-template='inkwash'] .iw-seal {
  display: grid;
  place-items: center;
  width: 9.5mm;
  height: 9.5mm;
  border-radius: 1mm;
  background: #a23b2c;
  color: #fbf4ec;
  font-size: 13pt;
  font-weight: 700;
  letter-spacing: 0;
  box-shadow: inset 0 0 0 0.5mm rgb(251 244 236 / 0.55);
}
[data-template='inkwash'] .iw-title { margin-top: 2.4mm; font-family: var(--r-font-heading); font-size: calc(var(--r-size) + 1.25pt); letter-spacing: 0.2em; color: var(--r-ink); }
[data-template='inkwash'] .iw-contact { margin-top: 3mm; }
[data-template='inkwash'] .rr-heading {
  padding-bottom: 2.4mm;
  color: var(--r-ink);
  font-size: calc(var(--r-size) + 2pt);
  font-weight: 600;
  letter-spacing: 0.3em;
  background: var(--iw-stroke) no-repeat 0 100% / 26mm auto;
}
[data-template='inkwash'] .rr-no-decor .rr-heading,
[data-template='inkwash'].rr-no-decor .rr-heading { background: none; border-bottom: 0.5pt solid var(--r-rule); }
[data-template='inkwash'] .rr-entry__title { font-family: var(--r-font-heading); font-size: calc(var(--r-size) + 1pt); }
[data-template='inkwash'] .rr-bullets li::before { background: var(--r-accent); }
</style>

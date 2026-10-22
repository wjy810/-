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
      <svg v-if="design.decorations" class="constellation-chart rr-decor" viewBox="0 0 120 120" fill="none" aria-hidden="true">
        <g stroke="currentColor" stroke-width=".4" opacity=".36">
          <circle cx="60" cy="60" r="53" />
          <circle cx="60" cy="60" r="36" stroke-dasharray="1 3" />
          <path d="M60 2v12m0 92v12M2 60h12m92 0h12M22.5 22.5l5 5m65 65 5 5M22.5 97.5l5-5m65-65 5-5" />
          <path d="M60 24v72M24 60h72" stroke-dasharray="1 4" />
        </g>
        <path d="m27 73 16-27 19 8 15-24 14 21-16 30-17-9-15-26" stroke="currentColor" stroke-width=".75" opacity=".72" />
        <g fill="currentColor">
          <circle cx="27" cy="73" r="1.3" /><circle cx="43" cy="46" r="1.8" />
          <circle cx="62" cy="54" r="1.2" /><circle cx="77" cy="30" r="1.6" />
          <circle cx="91" cy="51" r="1" /><circle cx="75" cy="81" r="1.7" /><circle cx="58" cy="72" r="1" />
        </g>
        <path d="M43 41v10m-5-5h10M75 77v8m-4-4h8" stroke="currentColor" stroke-width=".6" />
        <g fill="currentColor" opacity=".48">
          <circle cx="39" cy="23" r=".65" /><circle cx="23" cy="50" r=".6" />
          <circle cx="95" cy="72" r=".7" /><circle cx="49" cy="94" r=".6" /><circle cx="69" cy="17" r=".5" />
        </g>
      </svg>
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
[data-template='constellation'] .collection-sheet { --c-side-width: 47mm; }
[data-template='constellation'] .collection-head { grid-template-columns: minmax(0, 1fr) auto auto; padding: 5mm 7mm 4mm; gap: 3mm 5mm; background: var(--r-accent); color: var(--r-on-accent); }
[data-template='constellation'] .collection-name { color: var(--r-on-accent); font-size: 31pt; letter-spacing: .08em; font-weight: 500; }
[data-template='constellation'] .collection-title { color: var(--r-on-accent); font-size: 10.5pt; letter-spacing: .04em; margin-top: 3mm; }
[data-template='constellation'] .constellation-chart { position: static; z-index: auto; width: 24mm; height: 24mm; color: var(--r-starlight); grid-column: 2; }
[data-template='constellation'] .collection-photo { grid-column: 3; border: .6pt solid color-mix(in srgb, var(--r-on-accent) 50%, transparent); }
[data-template='constellation'] .collection-contact { color: var(--r-on-accent); border-top: .5pt solid color-mix(in srgb, var(--r-on-accent) 25%, transparent); padding-top: 2.5mm; font-size: 8pt; }
[data-template='constellation'] .rr-contact__icon { stroke: currentColor; }
[data-template='constellation'] .rr-contact__sep { color: color-mix(in srgb, var(--r-on-accent) 44%, transparent); }
[data-template='constellation'] .collection-side { border-left: .5pt solid var(--r-rule); padding-left: 5mm; }
[data-template='constellation'] .rr-heading { gap: 3mm; letter-spacing: .06em; }
[data-template='constellation'] .rr-heading::after { content: ''; height: .6pt; flex: 1; background: var(--r-rule); align-self: center; }
[data-template='constellation'] .collection-side .rr-heading { border-top: 1pt solid var(--r-accent); padding-top: 2mm; }
[data-template='constellation'] .collection-side .rr-heading::after { content: none; }
[data-template='constellation'] .rr-entry__date { font-family: var(--r-font-mono); font-size: calc(var(--r-size-sm) - .3pt); }
[data-template='constellation'][data-header='compact'] .collection-head { padding: 4mm 6mm; }
[data-template='constellation'][data-header='compact'] .constellation-chart { width: 19mm; height: 19mm; }
</style>

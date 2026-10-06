<script setup lang="ts">
import type { LayoutProps } from '../../components/context'
import ContactList from '../../components/primitives/ContactList.vue'
import ResumePhoto from '../../components/primitives/ResumePhoto.vue'
import RRegion from '../../components/RRegion.vue'

defineProps<LayoutProps>()
</script>

<template>
  <div class="rr-sheet">
    <header v-if="!page.continuation" class="tl-head">
      <div class="tl-identity">
        <h1 class="tl-name">{{ document.header.name }}</h1>
        <p v-if="document.header.title" class="tl-title">{{ document.header.title }}</p>
        <ContactList :header="document.header" :icons="design.contactIcons" class="tl-contact" />
      </div>
      <ResumePhoto v-if="document.header.photo && design.headerVariant !== 'plain'" :src="document.header.photo" :shape="design.photo.shape" class="tl-photo" />
    </header>
    <div v-else class="rr-continue"><strong>{{ document.header.name }}</strong><span>{{ document.header.title }}</span></div>
    <div class="rr-body"><RRegion id="main" :locale="document.locale" /></div>
  </div>
</template>

<style>
/* Line and dots are backgrounds, not positioned elements, so PDF text order stays the reading order. */
[data-template='timeline'] {
  --tl-gutter: 25mm;
  --tl-line: calc(var(--tl-gutter) - 3.2mm);
  --tl-rule: linear-gradient(var(--r-accent-tint), var(--r-accent-tint));
  --tl-dot-y: calc(var(--r-size-title) * 1.35 * 0.5 - 2.6pt);
}
[data-template='timeline'] .tl-head { display: flex; align-items: center; justify-content: space-between; gap: 8mm; margin-bottom: 6mm; }
[data-template='timeline'] .tl-name { font-family: var(--r-font-heading); font-size: 26pt; font-weight: 700; line-height: 1.1; letter-spacing: 0.03em; }
[data-template='timeline'] .tl-title { margin-top: 1.8mm; color: var(--r-accent); font-size: calc(var(--r-size) + 1.5pt); font-weight: 600; }
[data-template='timeline'] .tl-contact { margin-top: 3.2mm; }
[data-template='timeline'] .tl-photo { flex: none; }
[data-template='timeline'] .rr-heading {
  gap: 0;
  padding-left: var(--tl-gutter);
  color: var(--r-accent);
  font-size: calc(var(--r-size) + 1.5pt);
  background: radial-gradient(circle closest-side, var(--r-accent) 96%, transparent 100%) no-repeat calc(var(--tl-line) - 3.4pt) 50% / 7.5pt 7.5pt;
}
/* Continuous line: through every timeline block, and below the first entry of a section. */
[data-template='timeline'] .rr-block--timeline:not(.rr-block--start) { background: var(--tl-rule) no-repeat var(--tl-line) 0 / 0.75pt 100%; }
[data-template='timeline'] .rr-block--timeline.rr-block--start { background: var(--tl-rule) no-repeat var(--tl-line) 100% / 0.75pt var(--r-item-gap); }
[data-template='timeline'] .rr-block--timeline.rr-block--start .rr-entry { background: var(--tl-rule) no-repeat var(--tl-line) 0 / 0.75pt 100%; }
[data-template='timeline'] .rr-summary,
[data-template='timeline'] .rr-skill,
[data-template='timeline'] .rr-list-item { padding-left: var(--tl-gutter); }
[data-template='timeline'] .rr-entry {
  grid-template-columns: var(--tl-gutter) minmax(0, 1fr);
  grid-template-areas: 'date title' 'date meta' 'date body';
  column-gap: 0;
}
[data-template='timeline'] .rr-entry__date { display: flex; flex-direction: column; padding-right: 6mm; line-height: 1.35; padding-top: 0.6pt; }
[data-template='timeline'] .rr-entry__sep { display: none; }
[data-template='timeline'] .rr-entry__end { font-size: calc(var(--r-size) - 1.75pt); }
/* Hollow dot on the line at each entry title, drawn over the line by the date cell's background. */
[data-template='timeline'] .rr-entry__date {
  background: radial-gradient(circle closest-side, var(--r-paper) 52%, var(--r-accent) 58%, var(--r-accent) 92%, transparent 100%) no-repeat calc(var(--tl-line) - 2.6pt) var(--tl-dot-y) / 6pt 6pt;
}
[data-template='timeline'] .rr-entry--cont .rr-entry__body { grid-column: 2; }
[data-template='timeline'] .rr-entry__meta { justify-content: flex-start; }
[data-template='timeline'] .rr-entry__location::before { content: '· '; }
</style>

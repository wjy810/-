<script setup lang="ts">
/** Tiny schematic of a header layout or section heading style, drawn in the accent colour. */
defineProps<{ kind: 'header' | 'heading'; value: string; accent?: string }>()
</script>

<template>
  <svg class="design-glyph" viewBox="0 0 64 40" aria-hidden="true" :style="{ '--glyph-accent': accent || 'var(--color-primary)' }">
    <template v-if="kind === 'header'">
      <template v-if="value === 'BAND'">
        <rect x="0" y="0" width="64" height="16" rx="2" fill="var(--glyph-accent)" />
        <rect x="8" y="5" width="22" height="3" rx="1.5" fill="#fff" />
        <rect x="8" y="10" width="14" height="2" rx="1" fill="#fff" opacity=".7" />
      </template>
      <template v-else-if="value === 'SPLIT'">
        <rect x="6" y="6" width="22" height="3.5" rx="1.75" fill="var(--glyph-accent)" />
        <rect x="6" y="12" width="14" height="2" rx="1" fill="currentColor" opacity=".35" />
        <rect x="44" y="4" width="13" height="14" rx="2" fill="currentColor" opacity=".18" />
      </template>
      <template v-else-if="value === 'COMPACT'">
        <rect x="6" y="6" width="16" height="3.5" rx="1.75" fill="var(--glyph-accent)" />
        <rect x="25" y="6.5" width="24" height="2.5" rx="1.25" fill="currentColor" opacity=".35" />
      </template>
      <template v-else>
        <rect x="21" y="5" width="22" height="3.5" rx="1.75" fill="var(--glyph-accent)" />
        <rect x="16" y="11" width="32" height="2" rx="1" fill="currentColor" opacity=".35" />
      </template>
      <rect x="6" y="22" width="52" height="2" rx="1" fill="currentColor" opacity=".2" />
      <rect x="6" y="27" width="44" height="2" rx="1" fill="currentColor" opacity=".2" />
      <rect x="6" y="32" width="48" height="2" rx="1" fill="currentColor" opacity=".2" />
    </template>
    <template v-else>
      <template v-if="value === 'BAR'">
        <rect x="6" y="7" width="52" height="8" rx="1.5" fill="var(--glyph-accent)" opacity=".18" />
        <rect x="9" y="9.5" width="18" height="3" rx="1.5" fill="var(--glyph-accent)" />
      </template>
      <template v-else-if="value === 'SIDELINE'">
        <rect x="6" y="6" width="2.5" height="28" rx="1.25" fill="var(--glyph-accent)" />
        <rect x="12" y="8" width="18" height="3" rx="1.5" fill="var(--glyph-accent)" />
      </template>
      <template v-else-if="value === 'TABLE'">
        <rect x="6" y="6" width="52" height="28" rx="1.5" fill="none" stroke="currentColor" stroke-opacity=".35" />
        <rect x="6" y="6" width="52" height="8" fill="var(--glyph-accent)" opacity=".18" />
        <rect x="9" y="8.5" width="16" height="3" rx="1.5" fill="var(--glyph-accent)" />
      </template>
      <template v-else-if="value === 'RULE'">
        <rect x="6" y="7" width="18" height="3" rx="1.5" fill="var(--glyph-accent)" />
        <rect x="6" y="13" width="52" height="1.2" fill="var(--glyph-accent)" />
      </template>
      <template v-else>
        <rect x="6" y="8" width="18" height="3" rx="1.5" fill="var(--glyph-accent)" />
      </template>
      <rect :x="value === 'SIDELINE' ? 12 : 9" y="19" width="42" height="2" rx="1" fill="currentColor" opacity=".25" />
      <rect :x="value === 'SIDELINE' ? 12 : 9" y="24" width="36" height="2" rx="1" fill="currentColor" opacity=".25" />
      <rect :x="value === 'SIDELINE' ? 12 : 9" y="29" width="40" height="2" rx="1" fill="currentColor" opacity=".25" />
    </template>
  </svg>
</template>

<style scoped>
.design-glyph {
  width: 100%;
  height: auto;
  display: block;
  color: var(--text-primary);
}
</style>

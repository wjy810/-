<script setup lang="ts">
/** JobProof AI brand mark: an ink-indigo tile with a paper sheet, an apricot proof stroke and a spark. */
withDefaults(defineProps<{ size?: number; wordmark?: boolean }>(), { size: 32, wordmark: true })
</script>

<template>
  <span class="brand" :style="{ '--brand-size': `${size}px` }">
    <svg class="brand__mark" :width="size" :height="size" viewBox="0 0 48 48" fill="none" aria-hidden="true">
      <defs>
        <linearGradient id="jp-brand-tile" x1="6" y1="4" x2="44" y2="46" gradientUnits="userSpaceOnUse">
          <stop offset="0" stop-color="#6F6AF0" />
          <stop offset="0.55" stop-color="#4A44D9" />
          <stop offset="1" stop-color="#312D90" />
        </linearGradient>
        <linearGradient id="jp-brand-proof" x1="15" y1="27" x2="31" y2="34" gradientUnits="userSpaceOnUse">
          <stop offset="0" stop-color="#FA8C55" />
          <stop offset="1" stop-color="#EF7339" />
        </linearGradient>
      </defs>
      <rect x="2" y="2" width="44" height="44" rx="13" fill="url(#jp-brand-tile)" />
      <rect x="2.5" y="2.5" width="43" height="43" rx="12.5" stroke="#fff" stroke-opacity="0.16" />
      <path d="M15.5 10.5h11.8l6.7 6.7v19.3a2.5 2.5 0 0 1-2.5 2.5H15.5a2.5 2.5 0 0 1-2.5-2.5V13a2.5 2.5 0 0 1 2.5-2.5Z" fill="#FDFBF7" />
      <path d="M27.3 10.5v4.7a2 2 0 0 0 2 2H34" fill="#E4E4FD" />
      <rect x="17" y="18.5" width="8" height="2.4" rx="1.2" fill="#A8A6F6" />
      <rect x="17" y="23" width="12" height="2.4" rx="1.2" fill="#CBCAFB" />
      <path d="M17.2 30.6l3.6 3.4 8.4-8.4" stroke="url(#jp-brand-proof)" stroke-width="3.2" stroke-linecap="round" stroke-linejoin="round" />
      <path d="M38.2 8.6c.35 2.2 1.45 3.3 3.65 3.65-2.2.35-3.3 1.45-3.65 3.65-.35-2.2-1.45-3.3-3.65-3.65 2.2-.35 3.3-1.45 3.65-3.65Z" fill="#FFD9C2" />
    </svg>
    <span v-if="wordmark" class="brand__word">JobProof<em>AI</em></span>
  </span>
</template>

<style scoped>
.brand {
  display: inline-flex;
  align-items: center;
  gap: calc(var(--brand-size) * 0.3);
  color: var(--text-primary);
  white-space: nowrap;
}

.brand__mark {
  flex-shrink: 0;
  filter: drop-shadow(0 4px 10px rgba(74, 68, 217, 0.28));
}

.brand__word {
  font-size: calc(var(--brand-size) * 0.56);
  font-weight: 750;
  letter-spacing: -0.03em;
  line-height: 1;
}

.brand__word em {
  margin-left: 0.18em;
  font-style: normal;
  background: var(--gradient-ai);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}
</style>

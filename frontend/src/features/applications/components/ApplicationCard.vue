<script setup lang="ts">
/** One application on the board: who, what, and the resume link's receipt at a glance. */
import { computed } from 'vue'
import { CalendarClock, Eye, EyeOff, Link2Off, Target } from 'lucide-vue-next'
import type { JobApplication } from '../types'
import { channelLabel, outcomeLabel, outcomeTone } from '../labels'
import { dueOf, receiptOf } from '../utils/receipts'

const props = defineProps<{ application: JobApplication; now: Date }>()
defineEmits<{ open: [] }>()

const receipt = computed(() => receiptOf(props.application.link, props.now))
const due = computed(() => (props.application.stage === 'CLOSED' ? null : dueOf(props.application.nextStepAt, props.now)))
const initial = computed(() => Array.from(props.application.company.trim())[0] ?? '?')
</script>

<template>
  <button type="button" class="app-card" :class="`is-${receipt.tone}`" @click="$emit('open')">
    <span class="app-card__head">
      <span class="app-card__logo" aria-hidden="true">{{ initial }}</span>
      <span class="app-card__who">
        <strong>{{ application.company }}</strong>
        <small>{{ application.positionTitle }}</small>
      </span>
      <span v-if="application.matchScore != null" class="app-card__score" :title="`岗位匹配 ${application.matchScore} 分`">
        <Target :size="11" />{{ application.matchScore }}
      </span>
    </span>

    <span class="app-card__receipt" :title="receipt.detail">
      <span class="app-card__pulse" aria-hidden="true">
        <Eye v-if="receipt.tone === 'fresh' || receipt.tone === 'seen'" :size="13" />
        <Link2Off v-else-if="receipt.tone === 'off' || receipt.tone === 'none'" :size="13" />
        <EyeOff v-else :size="13" />
      </span>
      <span>{{ receipt.label }}</span>
    </span>

    <span class="app-card__foot">
      <span class="app-card__tag">{{ channelLabel(application.channel) }}</span>
      <span v-if="application.outcome" class="app-card__outcome" :class="`is-${outcomeTone(application.outcome)}`">{{ outcomeLabel(application.outcome) }}</span>
      <span v-if="due" class="app-card__due" :class="`is-${due.tone}`">
        <CalendarClock :size="12" />{{ application.nextStep ? `${application.nextStep} · ` : '' }}{{ due.label }}
      </span>
    </span>
  </button>
</template>

<style scoped>
.app-card {
  width: 100%;
  display: grid;
  gap: 10px;
  padding: 12px;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  color: var(--text-primary);
  background: var(--surface-1);
  box-shadow: var(--shadow-xs, 0 1px 2px rgb(15 23 42 / 0.05));
  font: inherit;
  text-align: left;
  cursor: grab;
  transition: border-color var(--dur-fast) var(--ease-out), box-shadow var(--dur-fast) var(--ease-out), transform var(--dur-fast) var(--ease-out);
}

.app-card:hover {
  border-color: var(--color-primary-border);
  box-shadow: var(--shadow-sm);
  transform: translateY(-1px);
}

.app-card:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 2px;
}

.app-card:active {
  cursor: grabbing;
}

.app-card__head {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.app-card__logo {
  width: 32px;
  height: 32px;
  flex: none;
  display: grid;
  place-items: center;
  border-radius: var(--radius-sm);
  color: var(--color-primary-text);
  background: var(--color-primary-soft);
  font-size: 14px;
  font-weight: 700;
}

.app-card__who {
  flex: 1;
  min-width: 0;
  display: grid;
  gap: 1px;
}

.app-card__who strong,
.app-card__who small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.app-card__who strong {
  font-size: var(--fs-sm);
  font-weight: 650;
}

.app-card__who small {
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
}

.app-card__score {
  flex: none;
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 1px 7px;
  border-radius: var(--radius-full);
  color: var(--color-primary-text);
  background: var(--color-primary-soft);
  font-size: 11px;
  font-weight: 650;
  font-variant-numeric: tabular-nums;
}

.app-card__receipt {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 6px 8px;
  border-radius: var(--radius-sm);
  color: var(--text-secondary);
  background: var(--surface-2);
  font-size: var(--fs-xs);
}

.app-card__pulse {
  position: relative;
  width: 20px;
  height: 20px;
  flex: none;
  display: grid;
  place-items: center;
  border-radius: 50%;
  color: var(--text-tertiary);
  background: var(--surface-1);
}

.app-card.is-fresh .app-card__receipt {
  color: var(--color-success-text);
  background: var(--color-success-soft);
}

.app-card.is-fresh .app-card__pulse {
  color: var(--color-success-text);
}

.app-card.is-fresh .app-card__pulse::after {
  position: absolute;
  inset: -2px;
  border: 2px solid var(--color-success);
  border-radius: 50%;
  opacity: 0;
  animation: app-card-ping 2.4s var(--ease-out) infinite;
  content: '';
}

.app-card.is-seen .app-card__receipt {
  color: var(--color-primary-text);
  background: var(--color-primary-soft);
}

.app-card.is-seen .app-card__pulse {
  color: var(--color-primary-text);
}

.app-card__foot {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  font-size: 11px;
}

.app-card__tag,
.app-card__outcome {
  padding: 1px 7px;
  border-radius: var(--radius-full);
  color: var(--text-secondary);
  background: var(--surface-3);
}

.app-card__outcome.is-success { color: var(--color-success-text); background: var(--color-success-soft); }
.app-card__outcome.is-danger { color: var(--color-danger-text); background: var(--color-danger-soft); }
.app-card__outcome.is-warning { color: var(--color-warning-text); background: var(--color-warning-soft); }

.app-card__due {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
  color: var(--text-tertiary);
}

.app-card__due.is-soon { color: var(--color-warning-text); }
.app-card__due.is-overdue { color: var(--color-danger-text); font-weight: 600; }

@keyframes app-card-ping {
  0% { opacity: 0.7; transform: scale(0.8); }
  80%, 100% { opacity: 0; transform: scale(1.6); }
}

@media (prefers-reduced-motion: reduce) {
  .app-card { transition: none; }
  .app-card:hover { transform: none; }
  .app-card.is-fresh .app-card__pulse::after { animation: none; }
}
</style>

<script setup lang="ts">
/** 投递 → 简历被打开 → 面试 → Offer, with each step's rate against the one before it. */
import { computed } from 'vue'
import { AlarmClock, ChevronRight } from 'lucide-vue-next'
import type { ApplicationStats } from '../types'
import { funnelOf } from '../utils/receipts'

const props = defineProps<{ stats: ApplicationStats }>()
const steps = computed(() => funnelOf(props.stats))
const peak = computed(() => Math.max(1, ...steps.value.map(step => step.value)))
const dueText = computed(() => [
  props.stats.overdue ? `${props.stats.overdue} 个下一步已逾期` : '',
  props.stats.upcoming ? `${props.stats.upcoming} 个在 3 天内` : '',
].filter(Boolean).join('，'))
</script>

<template>
  <section class="funnel" aria-label="投递漏斗">
    <ol class="funnel__steps">
      <li v-for="(step, index) in steps" :key="step.key" class="funnel__step" :class="`is-${step.key}`">
        <ChevronRight v-if="index" class="funnel__arrow" :size="16" aria-hidden="true" />
        <div class="funnel__body">
          <span class="funnel__label">{{ step.label }}</span>
          <strong class="funnel__value">{{ step.value }}</strong>
          <span class="funnel__bar" aria-hidden="true"><i :style="{ width: `${Math.max(4, (step.value / peak) * 100)}%` }" /></span>
          <small :title="step.hint">{{ step.rate == null ? step.hint : `转化 ${step.rate}%` }}</small>
        </div>
      </li>
    </ol>
    <div v-if="stats.overdue || stats.upcoming" class="funnel__due" :class="{ 'is-overdue': stats.overdue }">
      <AlarmClock :size="16" aria-hidden="true" />
      <span>{{ dueText }}</span>
    </div>
  </section>
</template>

<style scoped>
.funnel {
  display: grid;
  gap: var(--space-3);
  padding: var(--space-4) var(--space-5);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  background:
    radial-gradient(120% 140% at 0% 0%, var(--color-primary-soft), transparent 55%),
    var(--surface-1);
  box-shadow: var(--shadow-sm);
}

.funnel__steps {
  list-style: none;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--space-2);
}

.funnel__step {
  position: relative;
  display: flex;
  align-items: center;
  gap: var(--space-2);
  min-width: 0;
}

.funnel__arrow {
  flex: none;
  color: var(--border-strong);
}

.funnel__body {
  flex: 1;
  min-width: 0;
  display: grid;
  gap: 2px;
}

.funnel__label {
  color: var(--text-secondary);
  font-size: var(--fs-xs);
}

.funnel__value {
  font-size: 26px;
  font-weight: 700;
  line-height: 1.15;
  font-variant-numeric: tabular-nums;
}

.funnel__bar {
  height: 4px;
  margin: 4px 0 2px;
  overflow: hidden;
  border-radius: var(--radius-full);
  background: var(--surface-3);
}

.funnel__bar i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--color-primary);
  transition: width var(--dur-slow, 400ms) var(--ease-out);
}

.funnel__step.is-opened .funnel__bar i { background: var(--color-success); }
.funnel__step.is-interviews .funnel__bar i { background: var(--color-warning); }
.funnel__step.is-offers .funnel__bar i { background: var(--color-accent, var(--color-success)); }

.funnel__body small {
  overflow: hidden;
  color: var(--text-tertiary);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.funnel__due {
  display: flex;
  align-items: center;
  gap: 6px;
  width: max-content;
  max-width: 100%;
  padding: 4px 10px;
  border-radius: var(--radius-full);
  color: var(--color-warning-text);
  background: var(--color-warning-soft);
  font-size: var(--fs-xs);
}

.funnel__due.is-overdue {
  color: var(--color-danger-text);
  background: var(--color-danger-soft);
}

@media (max-width: 720px) {
  .funnel { padding: var(--space-4); }
  .funnel__steps { grid-template-columns: repeat(2, minmax(0, 1fr)); row-gap: var(--space-3); }
  .funnel__step:nth-child(3) .funnel__arrow { display: none; }
  .funnel__value { font-size: 22px; }
}
</style>

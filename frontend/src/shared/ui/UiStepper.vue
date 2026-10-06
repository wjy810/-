<script setup lang="ts">
import { Check } from 'lucide-vue-next'

defineProps<{ steps: { key: string; label: string }[]; current: number }>()
</script>

<template>
  <ol class="ui-stepper" :aria-label="`第 ${current + 1} 步，共 ${steps.length} 步`">
    <li
      v-for="(step, index) in steps"
      :key="step.key"
      class="ui-stepper__step"
      :class="{ 'is-done': index < current, 'is-current': index === current }"
      :aria-current="index === current ? 'step' : undefined"
    >
      <span class="ui-stepper__dot">
        <Check v-if="index < current" :size="13" :stroke-width="3" />
        <template v-else>{{ index + 1 }}</template>
      </span>
      <span class="ui-stepper__label">{{ step.label }}</span>
    </li>
  </ol>
  <p class="ui-stepper__mobile">第 {{ current + 1 }} 步 / 共 {{ steps.length }} 步 · {{ steps[current]?.label }}</p>
</template>

<style scoped>
.ui-stepper {
  display: flex;
  align-items: center;
  gap: 0;
  list-style: none;
}

.ui-stepper__step {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  color: var(--text-tertiary);
  font-size: var(--fs-sm);
  font-weight: 500;
}

.ui-stepper__step:not(:last-child)::after {
  content: '';
  flex: 1;
  height: 2px;
  margin: 0 12px;
  border-radius: 2px;
  background: var(--border-default);
  transition: background-color var(--dur-slow) var(--ease-out);
}

.ui-stepper__step.is-done:not(:last-child)::after {
  background: var(--color-primary);
}

.ui-stepper__dot {
  width: 26px;
  height: 26px;
  flex-shrink: 0;
  display: grid;
  place-items: center;
  border: 1.5px solid var(--border-strong);
  border-radius: 50%;
  background: var(--surface-1);
  font-size: var(--fs-xs);
  font-weight: 650;
  font-variant-numeric: tabular-nums;
  transition: all var(--dur-base) var(--ease-out);
}

.ui-stepper__step.is-current {
  color: var(--text-primary);
  font-weight: 600;
}

.ui-stepper__step.is-current .ui-stepper__dot {
  border-color: var(--color-primary);
  background: var(--color-primary);
  color: #fff;
  box-shadow: 0 0 0 4px var(--color-primary-soft);
}

.ui-stepper__step.is-done {
  color: var(--text-secondary);
}

.ui-stepper__step.is-done .ui-stepper__dot {
  border-color: var(--color-primary);
  background: var(--color-primary-soft);
  color: var(--color-primary-text);
}

.ui-stepper__label {
  white-space: nowrap;
}

.ui-stepper__mobile {
  display: none;
  color: var(--text-secondary);
  font-size: var(--fs-sm);
  font-weight: 600;
}

@media (max-width: 720px) {
  .ui-stepper__label {
    display: none;
  }

  .ui-stepper__step.is-current .ui-stepper__label {
    display: inline;
  }
}

@media (max-width: 480px) {
  .ui-stepper {
    display: none;
  }

  .ui-stepper__mobile {
    display: block;
  }
}
</style>

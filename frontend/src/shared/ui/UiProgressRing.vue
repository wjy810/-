<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'

const props = withDefaults(
  defineProps<{ value: number; size?: number; stroke?: number; tone?: 'primary' | 'success' | 'warning' | 'danger' | 'ai' | 'auto'; label?: string; showValue?: boolean; suffix?: string }>(),
  { size: 64, stroke: 6, tone: 'primary', label: '', showValue: true, suffix: '' },
)

const clamped = computed(() => Math.max(0, Math.min(100, Number.isFinite(props.value) ? props.value : 0)))
const radius = computed(() => (props.size - props.stroke) / 2)
const circumference = computed(() => 2 * Math.PI * radius.value)
const displayed = ref(0)
const offset = computed(() => circumference.value * (1 - displayed.value / 100))
const resolvedTone = computed(() => {
  if (props.tone !== 'auto') return props.tone
  return clamped.value >= 75 ? 'success' : clamped.value >= 50 ? 'primary' : clamped.value >= 30 ? 'warning' : 'danger'
})
const gradientId = `ring-ai-${Math.random().toString(36).slice(2, 8)}`

// Count up/down smoothly to the new value.
let frame = 0
function animateTo(target: number): void {
  cancelAnimationFrame(frame)
  const start = displayed.value
  const startedAt = performance.now()
  const duration = 700
  const reduce = typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
  if (reduce) {
    displayed.value = target
    return
  }
  const step = (now: number) => {
    const t = Math.min(1, (now - startedAt) / duration)
    const eased = 1 - Math.pow(1 - t, 3)
    displayed.value = start + (target - start) * eased
    if (t < 1) frame = requestAnimationFrame(step)
  }
  frame = requestAnimationFrame(step)
}

onMounted(() => animateTo(clamped.value))
watch(clamped, value => animateTo(value))
</script>

<template>
  <div
    class="ui-ring"
    :class="`ui-ring--${resolvedTone}`"
    :style="{ width: `${size}px`, height: `${size}px` }"
    role="progressbar"
    :aria-valuenow="Math.round(clamped)"
    aria-valuemin="0"
    aria-valuemax="100"
    :aria-label="label || undefined"
  >
    <svg :width="size" :height="size" :viewBox="`0 0 ${size} ${size}`">
      <defs v-if="resolvedTone === 'ai'">
        <linearGradient :id="gradientId" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" stop-color="#5e59e8" />
          <stop offset="55%" stop-color="#9b6bf2" />
          <stop offset="100%" stop-color="#fa8c55" />
        </linearGradient>
      </defs>
      <circle class="ui-ring__track" :cx="size / 2" :cy="size / 2" :r="radius" :stroke-width="stroke" fill="none" />
      <circle
        class="ui-ring__bar"
        :cx="size / 2"
        :cy="size / 2"
        :r="radius"
        :stroke-width="stroke"
        fill="none"
        stroke-linecap="round"
        :stroke-dasharray="circumference"
        :stroke-dashoffset="offset"
        :stroke="resolvedTone === 'ai' ? `url(#${gradientId})` : undefined"
        :transform="`rotate(-90 ${size / 2} ${size / 2})`"
      />
    </svg>
    <div v-if="showValue || $slots.default" class="ui-ring__center">
      <slot><span class="ui-ring__value">{{ Math.round(displayed) }}<small v-if="suffix">{{ suffix }}</small></span></slot>
    </div>
  </div>
</template>

<style scoped>
.ui-ring {
  position: relative;
  display: inline-grid;
  place-items: center;
  flex-shrink: 0;
}

.ui-ring svg {
  position: absolute;
  inset: 0;
}

.ui-ring__track {
  stroke: var(--surface-3);
}

.ui-ring__bar {
  stroke: var(--color-primary);
}

.ui-ring--success .ui-ring__bar {
  stroke: var(--color-success);
}

.ui-ring--warning .ui-ring__bar {
  stroke: var(--color-warning);
}

.ui-ring--danger .ui-ring__bar {
  stroke: var(--color-danger);
}

.ui-ring__center {
  position: relative;
  display: grid;
  place-items: center;
  text-align: center;
}

.ui-ring__value {
  font-size: 1.05em;
  font-weight: 700;
  letter-spacing: -0.02em;
  font-variant-numeric: tabular-nums;
}

.ui-ring__value small {
  margin-left: 1px;
  font-size: 0.6em;
  font-weight: 600;
  color: var(--text-tertiary);
}
</style>

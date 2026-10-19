<script setup lang="ts">
/**
 * One series per day as columns (the dashboard draws one chart per measure, never two scales on one).
 * Drawn at the measured width so text and the 4px rounded caps stay crisp; columns are at most 24px.
 * Only the peak and today carry a value label; every column has its own hover / focus tooltip.
 */
import { computed, ref } from 'vue'
import { useElementSize } from '@vueuse/core'
import { niceMax, shortDay } from '../format'

const props = defineProps<{ title: string; unit: string; points: { day: string; value: number }[] }>()

const root = ref<HTMLElement | null>(null)
const { width } = useElementSize(root)
const HEIGHT = 168
const PAD = { top: 18, right: 4, bottom: 24, left: 30 }
const active = ref<number | null>(null)

const top = computed(() => niceMax(props.points.map(point => point.value)))
const plotWidth = computed(() => Math.max(0, width.value - PAD.left - PAD.right))
const plotHeight = HEIGHT - PAD.top - PAD.bottom
const slot = computed(() => (props.points.length ? plotWidth.value / props.points.length : 0))
const barWidth = computed(() => Math.max(4, Math.min(24, slot.value * 0.56)))
const ticks = computed(() => [0, top.value / 2, top.value])
const peak = computed(() => props.points.reduce((best, point, index) => (point.value > (props.points[best]?.value ?? 0) ? index : best), 0))

const y = (value: number) => PAD.top + plotHeight - (value / top.value) * plotHeight
const x = (index: number) => PAD.left + slot.value * index + (slot.value - barWidth.value) / 2

/** A column with a 4px rounded cap and a square foot on the baseline. */
function column(index: number, value: number): string {
  const left = x(index)
  const w = barWidth.value
  const base = y(0)
  const height = Math.max(0, base - y(value))
  if (height <= 0) return ''
  const r = Math.min(4, w / 2, height)
  const t = base - height
  return `M${left},${base}V${t + r}Q${left},${t} ${left + r},${t}H${left + w - r}Q${left + w},${t} ${left + w},${t + r}V${base}Z`
}

const labelled = (index: number) => props.points[index]!.value > 0 && (index === peak.value || index === props.points.length - 1)
/** Day labels step back from today, as far apart as a label needs (about 36px). */
const dayStep = computed(() => Math.max(1, Math.ceil(36 / Math.max(1, slot.value))))
const showDay = (index: number) => (props.points.length - 1 - index) % dayStep.value === 0
const tooltipLeft = computed(() => (active.value === null ? 0 : Math.min(Math.max(x(active.value) + barWidth.value / 2, 60), width.value - 60)))
const tickLabel = (value: number) => (Number.isInteger(value) ? String(value) : value.toFixed(1))
</script>

<template>
  <figure class="daily">
    <figcaption>
      <span>{{ title }}</span>
      <small>近 {{ points.length }} 天</small>
    </figcaption>
    <div ref="root" class="daily__plot" @pointerleave="active = null">
      <svg v-if="width" :width="width" :height="HEIGHT" role="group" :aria-label="`${title}，近 ${points.length} 天`">
        <g class="daily__grid" aria-hidden="true">
          <template v-for="tick in ticks" :key="tick">
            <line :x1="PAD.left" :x2="width - PAD.right" :y1="y(tick)" :y2="y(tick)" />
            <text :x="PAD.left - 8" :y="y(tick)" dy="0.32em" text-anchor="end">{{ tickLabel(tick) }}</text>
          </template>
        </g>
        <g
          v-for="(point, index) in points"
          :key="point.day"
          class="daily__col"
          :class="{ 'is-active': active === index, 'is-today': index === points.length - 1 }"
          tabindex="0"
          role="img"
          :aria-label="`${point.day}：${point.value} ${unit}`"
          @pointerenter="active = index"
          @focus="active = index"
          @blur="active = null"
        >
          <rect class="daily__hit" :x="PAD.left + slot * index" :y="PAD.top" :width="slot" :height="plotHeight" />
          <path :d="column(index, point.value)" />
          <text v-if="labelled(index)" class="daily__value" :x="x(index) + barWidth / 2" :y="y(point.value) - 6" text-anchor="middle">{{ point.value }}</text>
          <text v-if="showDay(index)" class="daily__day" :x="x(index) + barWidth / 2" :y="HEIGHT - 6" text-anchor="middle">{{ shortDay(point.day) }}</text>
        </g>
      </svg>
      <div v-if="active !== null && points[active]" class="daily__tooltip" :style="{ left: `${tooltipLeft}px` }" role="status">
        <strong>{{ points[active]!.value }} {{ unit }}</strong>
        <span>{{ points[active]!.day }}</span>
      </div>
    </div>
  </figure>
</template>

<style scoped>
.daily {
  margin: 0;
  display: grid;
  gap: var(--space-2);
  min-width: 0;
}

.daily figcaption {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-2);
  font-size: var(--fs-sm);
  font-weight: 600;
}

.daily figcaption small {
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
  font-weight: 500;
}

.daily__plot {
  position: relative;
  min-height: 168px;
}

.daily svg {
  display: block;
  overflow: visible;
}

.daily__grid line {
  stroke: var(--border-subtle);
  stroke-width: 1;
  shape-rendering: crispEdges;
}

.daily__grid text,
.daily__day {
  fill: var(--text-tertiary);
  font-size: 11px;
  font-variant-numeric: tabular-nums;
}

.daily__col {
  outline: none;
  cursor: default;
}

.daily__hit {
  fill: transparent;
}

.daily__col path {
  fill: var(--color-primary);
  opacity: 0.78;
  transition: opacity var(--dur-fast) var(--ease-out);
}

.daily__col.is-today path,
.daily__col.is-active path {
  opacity: 1;
}

.daily__col:focus-visible .daily__hit {
  fill: var(--color-primary-soft);
}

.daily__value {
  fill: var(--text-primary);
  font-size: 11px;
  font-weight: 650;
  font-variant-numeric: tabular-nums;
}

.daily__tooltip {
  position: absolute;
  top: -6px;
  transform: translate(-50%, -100%);
  display: grid;
  gap: 1px;
  padding: 6px 10px;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  background: var(--surface-overlay, var(--surface-1));
  box-shadow: var(--shadow-md);
  pointer-events: none;
  white-space: nowrap;
}

.daily__tooltip strong {
  font-size: var(--fs-sm);
  font-weight: 650;
}

.daily__tooltip span {
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
}
</style>

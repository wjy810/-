/**
 * Draggable two-pane split with keyboard support and a per-key remembered ratio (docs/03 §4.7).
 * The ratio is the start pane's share of the container, in percent.
 */
import { computed, onMounted, ref, type Ref } from 'vue'
import { useResizeObserver } from '@vueuse/core'
import { usePreferencesStore } from '@/stores/preferences'

export type SplitPaneOptions = {
  key: string
  container: Ref<HTMLElement | null>
  defaultRatio?: number
  /** Minimum pixel widths of the start and end panes. */
  minStart?: number
  minEnd?: number
  /** Hard percentage bounds regardless of container width. */
  bounds?: [number, number]
  step?: number
}

export function useSplitPane(options: SplitPaneOptions) {
  const preferences = usePreferencesStore()
  const defaultRatio = options.defaultRatio ?? 50
  const [hardMin, hardMax] = options.bounds ?? [20, 80]
  const ratio = ref(preferences.splitRatio[options.key] ?? defaultRatio)
  const dragging = ref(false)
  const width = ref(0)

  const limits = computed(() => {
    const total = width.value || (typeof window === 'undefined' ? 1280 : window.innerWidth)
    const min = Math.max(hardMin, ((options.minStart ?? 0) / total) * 100)
    const max = Math.min(hardMax, ((total - (options.minEnd ?? 0)) / total) * 100)
    return min <= max ? { min, max } : { min: 50, max: 50 }
  })
  const clampedRatio = computed(() => Math.min(limits.value.max, Math.max(limits.value.min, ratio.value)))

  function set(value: number, persist = false): void {
    ratio.value = Math.round(Math.min(limits.value.max, Math.max(limits.value.min, value)) * 10) / 10
    if (persist) preferences.rememberSplit(options.key, ratio.value)
  }

  function fromPointer(event: PointerEvent): void {
    const rect = options.container.value?.getBoundingClientRect()
    if (!rect || rect.width <= 0) return
    set(((event.clientX - rect.left) / rect.width) * 100)
  }

  function onPointerDown(event: PointerEvent): void {
    if (event.button !== 0) return
    dragging.value = true
    ;(event.currentTarget as HTMLElement).setPointerCapture(event.pointerId)
    fromPointer(event)
  }

  function onPointerMove(event: PointerEvent): void {
    if (dragging.value) fromPointer(event)
  }

  function onPointerUp(event: PointerEvent): void {
    if (!dragging.value) return
    dragging.value = false
    const target = event.currentTarget as HTMLElement
    if (target.hasPointerCapture(event.pointerId)) target.releasePointerCapture(event.pointerId)
    preferences.rememberSplit(options.key, ratio.value)
  }

  function onKeydown(event: KeyboardEvent): void {
    const step = options.step ?? 2
    if (event.key === 'ArrowLeft' || event.key === 'ArrowRight') {
      event.preventDefault()
      set(clampedRatio.value + (event.key === 'ArrowLeft' ? -step : step), true)
    } else if (event.key === 'Home' || event.key === 'End') {
      event.preventDefault()
      set(event.key === 'Home' ? limits.value.min : limits.value.max, true)
    } else if (event.key === 'Enter') {
      event.preventDefault()
      reset()
    }
  }

  function reset(): void {
    set(defaultRatio, true)
  }

  useResizeObserver(options.container, (entries) => {
    width.value = entries[0]?.contentRect.width ?? width.value
  })
  onMounted(() => {
    width.value = options.container.value?.getBoundingClientRect().width ?? 0
  })

  return {
    ratio: clampedRatio,
    dragging,
    limits,
    set,
    reset,
    handlers: { onPointerdown: onPointerDown, onPointermove: onPointerMove, onPointerup: onPointerUp, onPointercancel: onPointerUp, onKeydown, onDblclick: reset },
  }
}

<script setup lang="ts">
/**
 * Renders a resume as real-size pages: measures every block on hidden probe pages, packs them with
 * paginate() and renders the result with the same components (docs/phase2/03 §3.3). Used by the
 * workbench preview, the template gallery and the print entry the renderer service loads.
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import '../fonts/fonts.css'
import './base.css'
import { buildBlocks, type Block } from '../layout/blocks'
import { autoFitTarget, paginate, type PaginationResult } from '../layout/paginate'
import { normalize } from '../model/normalize'
import { AUTO_FIT_LEVELS, type ResumeDesignV2 } from '../theme/design'
import { coerceDesign, PX_PER_MM, resolveTheme } from '../theme/tokens'
import type { TemplateModule } from '../templates/manifest'
import ResumePage from './ResumePage.vue'

export interface LayoutResult {
  pageCount: number
  pageLimit: number
  overflowMm: number
  firstOverflowSection: string | null
  accentAdjusted: string | null
  /** AUTO page target only: how far spacing was tightened to save a page (0 = as designed, 1 = most). */
  autoFit: number
}

const props = withDefaults(defineProps<{
  template: TemplateModule
  content: unknown
  design?: unknown
  photo?: string | null
  mode?: 'preview' | 'print' | 'thumbnail'
  interactive?: boolean
  /** Scale pages to the container width (preview / thumbnail). */
  fit?: boolean
  /** Fixed zoom when not fitting. */
  zoom?: number
  /** Delay before re-measuring after a change (preview typing). */
  debounceMs?: number
}>(), { photo: null, mode: 'preview', interactive: false, fit: true, zoom: 1, debounceMs: 0 })

const emit = defineEmits<{ layout: [result: LayoutResult]; sectionClick: [key: string] }>()

const manifest = computed(() => props.template.manifest)
const design = computed<ResumeDesignV2>(() => coerceDesign(manifest.value, props.design))
/** The design as chosen; relayout runs whenever it changes. */
const baseTheme = computed(() => resolveTheme(manifest.value, design.value))
/** Auto-fit level the visible pages use, and the one the measuring copy is trying. */
const appliedFit = ref(0)
const trialFit = ref(0)
const theme = computed(() => (appliedFit.value ? resolveTheme(manifest.value, design.value, appliedFit.value) : baseTheme.value))
const measureTheme = computed(() => (trialFit.value ? resolveTheme(manifest.value, design.value, trialFit.value) : baseTheme.value))
const photo = computed(() => (manifest.value.photo !== 'none' && design.value.photo.mode !== 'HIDE' && props.photo ? props.photo : null))
const doc = computed(() => normalize(props.content, design.value, {
  locale: manifest.value.locale, photo: photo.value, templateTitles: manifest.value.sectionTitles,
}))
/** Attributes and classes template styles key on; shared by the visible paper and the measuring copy. */
const paperAttrs = computed(() => ({
  lang: manifest.value.locale,
  'data-template': manifest.value.id,
  'data-palette': design.value.paletteId,
  'data-header': design.value.headerVariant,
  'data-photo-shape': design.value.photo.shape.toLowerCase(),
  'data-fonts': design.value.fontPairing,
}))
const paperClasses = computed(() => ({ 'rr-no-decor': !design.value.decorations, 'rr-no-icons': !design.value.contactIcons }))
const blocks = computed(() => buildBlocks(doc.value, manifest.value, design.value))
const byId = computed(() => new Map(blocks.value.map(block => [block.id, block])))
const allByRegion = computed(() => groupByRegion(blocks.value))
const pageLimit = computed(() => (design.value.pageTarget === 'ONE' ? 1 : design.value.pageTarget === 'TWO' ? 2 : manifest.value.maxPages))

const pages = shallowRef<Array<Record<string, Block[]>> | null>(null)
const result = shallowRef<LayoutResult | null>(null)
const measureRoot = ref<HTMLElement | null>(null)
const viewport = ref<HTMLElement | null>(null)
const containerWidth = ref(0)

function groupByRegion(list: Block[]): Record<string, Block[]> {
  const groups: Record<string, Block[]> = Object.fromEntries(manifest.value.regions.map(region => [region.id, [] as Block[]]))
  for (const block of list) (groups[block.region] ??= []).push(block)
  return groups
}

function regionCapacity(page: Element, scale: number): Record<string, number> {
  const capacity: Record<string, number> = {}
  page.querySelectorAll<HTMLElement>('[data-region]').forEach((region) => {
    const style = getComputedStyle(region)
    const height = region.getBoundingClientRect().height / scale - parseFloat(style.paddingTop) - parseFloat(style.paddingBottom)
    capacity[region.dataset.region!] = Math.max(0, height)
  })
  return capacity
}

let generation = 0
let timer: ReturnType<typeof setTimeout> | undefined

interface Measured { packed: PaginationResult }

/** Measures every block and the region capacities on the hidden probe pages, then packs pages. */
function measure(): Measured | null {
  const root = measureRoot.value
  if (!root) return null
  const [contentProbe, firstProbe, nextProbe] = Array.from(root.querySelectorAll<HTMLElement>(':scope > .rr-page'))
  if (!contentProbe || !firstProbe || !nextProbe) return null
  const pagePx = theme.value.pageMm.width * PX_PER_MM
  const scale = contentProbe.getBoundingClientRect().width / pagePx || 1
  const measured = blocks.value.map((block) => {
    const element = contentProbe.querySelector<HTMLElement>(`[data-block="${CSS.escape(block.id)}"]`)
    return { id: block.id, region: block.region, section: block.sectionKey, height: element ? element.getBoundingClientRect().height / scale : 0 }
  })
  return { packed: paginate(measured, { first: regionCapacity(firstProbe, scale), next: regionCapacity(nextProbe, scale) }, pageLimit.value) }
}

/**
 * AUTO page target: when the content runs past the page limit or leaves a nearly empty last page,
 * tighten spacing and line height step by step on the measuring copy and keep the first level that
 * saves the page. Explicit one- or two-page targets keep the spacing exactly as designed.
 */
async function autoFit(first: Measured, token: number): Promise<{ measured: Measured; fit: number } | null> {
  const target = design.value.pageTarget === 'AUTO' ? autoFitTarget(first.packed, pageLimit.value) : null
  if (target === null) return { measured: first, fit: 0 }
  for (const level of AUTO_FIT_LEVELS) {
    trialFit.value = level
    await nextTick()
    if (token !== generation) return null
    const attempt = measure()
    if (!attempt) return null
    if (attempt.packed.pageCount <= target) return { measured: attempt, fit: level }
  }
  return { measured: first, fit: 0 }
}

/** Hides contact separators that end a wrapped line, so no line ends on a dangling "·" or "|". */
function hideLineEndSeparators(): void {
  viewport.value?.querySelectorAll<HTMLElement>('.rr-contact').forEach((list) => {
    const items = Array.from(list.querySelectorAll<HTMLElement>(':scope > .rr-contact__item'))
    items.forEach((item, index) => {
      const separator = item.querySelector<HTMLElement>('.rr-contact__sep')
      if (!separator) return
      const next = items[index + 1]
      const wraps = !next || next.getBoundingClientRect().top > item.getBoundingClientRect().top + 1
      separator.toggleAttribute('data-line-end', wraps)
    })
  })
}

async function relayout(): Promise<LayoutResult | null> {
  const token = ++generation
  trialFit.value = 0
  await nextTick()
  if (typeof document !== 'undefined' && document.fonts) await document.fonts.ready
  if (token !== generation) return null
  const first = measure()
  if (!first) return null
  const fitted = await autoFit(first, token)
  if (!fitted || token !== generation) return null
  const { packed } = fitted.measured
  appliedFit.value = fitted.fit
  pages.value = packed.pages.map(page => Object.fromEntries(Object.entries(page).map(([region, ids]) => [region, ids.map(id => byId.value.get(id)!)])))
  const next: LayoutResult = {
    pageCount: packed.pageCount,
    pageLimit: pageLimit.value,
    overflowMm: Math.round((packed.overflowPx / PX_PER_MM) * 10) / 10,
    firstOverflowSection: packed.firstOverflowSection,
    accentAdjusted: theme.value.accentAdjusted ? theme.value.accent : null,
    autoFit: fitted.fit,
  }
  await nextTick()
  if (token !== generation) return null
  hideLineEndSeparators()
  result.value = next
  emit('layout', next)
  return next
}

function schedule(): void {
  if (timer) clearTimeout(timer)
  if (!props.debounceMs) {
    void relayout()
    return
  }
  timer = setTimeout(() => void relayout(), props.debounceMs)
}

watch([blocks, baseTheme, photo], schedule)

function onFontsLoaded(): void {
  schedule()
}

let observer: ResizeObserver | undefined
onMounted(() => {
  void relayout()
  document.fonts?.addEventListener?.('loadingdone', onFontsLoaded)
  if (props.fit && viewport.value && typeof ResizeObserver !== 'undefined') {
    observer = new ResizeObserver(([entry]) => { containerWidth.value = entry?.contentRect.width ?? 0 })
    observer.observe(viewport.value)
  }
})
onBeforeUnmount(() => {
  generation += 1
  if (timer) clearTimeout(timer)
  observer?.disconnect()
  document.fonts?.removeEventListener?.('loadingdone', onFontsLoaded)
})

const shownPages = computed(() => {
  const list = pages.value ?? [allByRegion.value]
  return props.mode === 'thumbnail' ? list.slice(0, 1) : list
})
const pageGapPx = computed(() => (props.mode === 'print' ? 0 : 24))
const scale = computed(() => {
  if (props.mode === 'print') return 1
  if (props.fit && containerWidth.value > 0) return Math.min(props.zoom > 1 ? props.zoom : 1, containerWidth.value / (theme.value.pageMm.width * PX_PER_MM))
  return props.zoom
})
const scaledBox = computed(() => {
  if (props.mode === 'print') return {}
  const width = theme.value.pageMm.width * PX_PER_MM
  const height = theme.value.pageMm.height * PX_PER_MM
  const count = shownPages.value.length
  return { width: `${width * scale.value}px`, height: `${(count * height + (count - 1) * pageGapPx.value) * scale.value}px` }
})

/** Clicks on a section emit its key; clicks on the page header (name, contacts) emit 'header'. */
function onClick(event: MouseEvent): void {
  if (!props.interactive) return
  const element = event.target as HTMLElement | null
  const target = element?.closest<HTMLElement>('.rr-pages [data-section]')
  if (target?.dataset.section) emit('sectionClick', target.dataset.section)
  else if (element?.closest('.rr-pages .rr-page header')) emit('sectionClick', 'header')
}

defineExpose({ relayout, result })
</script>

<template>
  <div
    class="rr-root"
    :class="[`rr-mode-${mode}`, paperClasses, { 'rr-interactive': interactive }]"
    v-bind="paperAttrs"
    :style="theme.vars"
    @click="onClick"
  >
    <!-- A full copy of the paper (same template selectors) that measures with the trial auto-fit spacing. -->
    <div ref="measureRoot" class="rr-root rr-measure" :class="paperClasses" v-bind="paperAttrs" :style="measureTheme.vars" aria-hidden="true">
      <ResumePage :template="template" :document="doc" :design="design" :theme="measureTheme" :index="0" :total="1" :blocks="allByRegion" probe />
      <ResumePage :template="template" :document="doc" :design="design" :theme="measureTheme" :index="0" :total="1" :blocks="{}" probe />
      <ResumePage :template="template" :document="doc" :design="design" :theme="measureTheme" :index="1" :total="2" :blocks="{}" probe />
    </div>
    <div ref="viewport" class="rr-viewport">
      <div class="rr-scaled" :style="scaledBox">
        <div class="rr-pages" :class="{ 'rr-pages--pending': !pages }" :style="mode === 'print' ? undefined : { transform: `scale(${scale})`, gap: `${pageGapPx}px` }">
          <ResumePage
            v-for="(page, index) in shownPages"
            :key="index"
            :template="template"
            :document="doc"
            :design="design"
            :theme="theme"
            :index="index"
            :total="pages?.length ?? 1"
            :blocks="page"
          />
        </div>
      </div>
    </div>
  </div>
</template>

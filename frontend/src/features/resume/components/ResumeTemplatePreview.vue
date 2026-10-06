<script setup lang="ts">
import { computed } from 'vue'
import type { ResumeDesignSettings, ResumeMasterView } from '../types'
import {
  paginatePreviewColumns,
  type PreviewPaginationColumn,
  type PreviewPaginationSection,
} from './resumePreviewPagination'
import { formatResumeContactLine } from './resumeContact'
import { projectResumeSection, type ResumeSectionProjection } from './resumeStructuredEntries'

type LayoutSlot = { key: string; order: number; label?: string; headingStyle?: string }
type LayoutColumn = { id: string; widthPercent: number; slotKeys: string[]; tone?: string }
type LayoutDefinition = {
  page?: { maxPages?: number; capacityUnits?: number; marginXPt?: number; marginTopPt?: number; marginBottomPt?: number }
  columns?: LayoutColumn[]
  slots?: LayoutSlot[]
  tokens?: Record<string, string>
  visual?: { headerStyle?: string; sectionStyle?: string; subtitle?: string; showMark?: boolean; density?: string }
}
type PreviewSection = PreviewPaginationSection
type PreviewColumn = LayoutColumn & PreviewPaginationColumn

const props = withDefaults(defineProps<{
  resume?: ResumeMasterView | null
  content?: Record<string, unknown> | null
  design?: ResumeDesignSettings | null
  templateId?: string | null
  variantCode?: string
  rendererProtocol?: string | null
  layoutDefinitionJson?: string | null
  photoUrl?: string | null
  compact?: boolean
  interactive?: boolean
}>(), {
  resume: null,
  content: null,
  design: null,
  templateId: null,
  variantCode: 'MONO',
  rendererProtocol: 'resume-layout-v1',
  layoutDefinitionJson: null,
  photoUrl: null,
  compact: false,
  interactive: false,
})

const emit = defineEmits<{ sectionClick: [key: string] }>()

const fallback: LayoutDefinition = {
  page: { maxPages: 1, marginXPt: 50, marginTopPt: 48, marginBottomPt: 48 },
  columns: [{ id: 'main', widthPercent: 100, slotKeys: ['summary', 'education', 'experience', 'projects', 'organizations', 'skills', 'certificates', 'honors', 'languages'] }],
  slots: [
    ['summary', 10, '个人简介'], ['education', 20, '教育经历'], ['experience', 30, '工作经历'],
    ['projects', 40, '项目经历'], ['organizations', 50, '社团与活动'], ['skills', 60, '专业技能'],
    ['certificates', 70, '证书与资质'], ['honors', 80, '荣誉奖项'], ['languages', 90, '语言能力'],
  ].map(([key, order, label]) => ({ key: String(key), order: Number(order), label: String(label) })),
  visual: { headerStyle: 'SPLIT', sectionStyle: 'RULE', subtitle: '结构化简历', showMark: true, density: 'STANDARD' },
}

const definition = computed<LayoutDefinition>(() => {
  if (!props.layoutDefinitionJson) return fallback
  try {
    const value = JSON.parse(props.layoutDefinitionJson) as LayoutDefinition
    return value.columns?.length && value.slots?.length ? value : fallback
  } catch {
    return fallback
  }
})

const variant = computed(() => props.variantCode.trim().toUpperCase() || 'DEFAULT')
const design = computed<ResumeDesignSettings>(() => props.design ?? {
  schemaVersion: 'resume-design-v1',
  fontPreset: 'MODERN_SANS', fontScale: 'STANDARD', lineHeight: 'STANDARD', pageMargin: 'STANDARD',
  accentColor: colorToken(`accent.${variant.value}`, '#1f2937'), dateFormat: 'YYYY_DOT_MM',
  headerLayout: definition.value.visual?.headerStyle ?? 'MINIMAL',
  headingStyle: definition.value.visual?.sectionStyle ?? 'RULE', photoMode: 'AUTO',
  density: definition.value.visual?.density ?? 'STANDARD', hiddenSections: [],
  sectionOrder: definition.value.slots?.map((slot) => slot.key) ?? [],
})
const visual = computed(() => ({ ...fallback.visual, ...definition.value.visual,
  headerStyle: design.value.headerLayout, sectionStyle: design.value.headingStyle, density: design.value.density }))
const basics = computed<Record<string, unknown>>(() => objectValue(props.content?.basics))
const intentions = computed<Record<string, unknown>>(() => objectValue(props.content?.intentions))
const displayName = computed(() => String(basics.value.name || props.resume?.title || '简历预览'))
const targetJob = computed(() => String(intentions.value.targetJob || ''))
const contactLine = computed(() => formatResumeContactLine(basics.value))
const displayPhoto = computed(() => Boolean(props.photoUrl) && design.value.photoMode !== 'HIDE')
const templateClass = computed(() => `resume-sheet--${(props.templateId || 'default').replace(/^rlt-b-/, '').replace(/-v\d+$/, '')}`)

const sheetStyle = computed(() => {
  const margin = design.value.pageMargin === 'NARROW' ? 5.8 : design.value.pageMargin === 'WIDE' ? 10.8
    : Math.max(6.4, Math.min(10, (definition.value.page?.marginXPt ?? 50) / 6.2))
  const fontScale = design.value.fontScale === 'SMALL' ? .9 : design.value.fontScale === 'LARGE' ? 1.08 : 1
  const line = design.value.lineHeight === 'COMPACT' ? 1.36 : design.value.lineHeight === 'AIRY' ? 1.68 : 1.5
  return {
    '--resume-accent': design.value.accentColor,
    '--resume-surface': surfaceFor(design.value.accentColor),
    '--resume-margin-x': `${margin}%`,
    '--resume-margin-top': `${design.value.pageMargin === 'NARROW' ? 4.8 : design.value.pageMargin === 'WIDE' ? 8 : 6}%`,
    '--resume-font-scale': String(fontScale), '--resume-line': String(line),
    '--resume-font': design.value.fontPreset === 'CLASSIC_SERIF'
      ? '"SimSun", "Songti SC", serif' : '"Microsoft YaHei", "Noto Sans CJK SC", Arial, sans-serif',
  }
})

const projections = computed<Record<string, ResumeSectionProjection>>(() => {
  if (props.content) return Object.fromEntries((fallback.slots ?? []).map((slot) => [
    slot.key,
    projectResumeSection(props.content ?? {}, slot.key, design.value.dateFormat),
  ]))
  return {
    summary: { value: props.resume?.selfIntro ?? '' }, education: { value: props.resume?.education ?? '' },
    experience: { value: props.resume?.experience ?? '' }, projects: { value: props.resume?.projects ?? '' },
    organizations: { value: '' }, skills: { value: props.resume?.skills ?? '' },
    certificates: { value: props.resume?.certificates ?? '' }, honors: { value: '' }, languages: { value: '' },
  }
})

const previewColumns = computed<PreviewColumn[]>(() => {
  const hidden = new Set(design.value.hiddenSections)
  const position = new Map(design.value.sectionOrder.map((key, index) => [key, index]))
  const slots = new Map((definition.value.slots ?? fallback.slots ?? []).map((slot) => [slot.key, slot]))
  const columns = (definition.value.columns ?? fallback.columns ?? []).map((column) => ({
    ...column,
    sections: column.slotKeys.filter((key) => !hidden.has(key)).sort((a, b) =>
      (position.get(a) ?? Number.MAX_SAFE_INTEGER) - (position.get(b) ?? Number.MAX_SAFE_INTEGER))
      .map((key): PreviewSection | null => {
        const slot = slots.get(key)
        if (!slot) return null
        const projection = projections.value[key] ?? { value: '' }
        const value = projection.value
        if (props.resume && !visible(value)) return null
        return { key, label: slot.label?.trim() || key, value,
          entries: projection.entries, headingStyle: design.value.headingStyle.toLowerCase(),
          order: position.get(key) ?? slot.order }
      }).filter((item): item is PreviewSection => Boolean(item)),
  }))
  return columns
})

const pages = computed<PreviewColumn[][]>(() => {
  return paginatePreviewColumns(previewColumns.value, {
    ...definition.value.page,
    density: design.value.density,
    fontScale: design.value.fontScale,
    lineHeight: design.value.lineHeight,
  }) as PreviewColumn[][]
})

function objectValue(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {}
}

function colorToken(key: string, fallbackColor: string): string {
  const value = definition.value.tokens?.[key]; return value && /^#[0-9a-f]{6}$/i.test(value) ? value : fallbackColor
}
function surfaceFor(color: string): string {
  const hex = color.replace('#', ''); if (!/^[0-9a-f]{6}$/i.test(hex)) return '#f3f5f8'
  const channels = [0, 2, 4].map((index) => Math.round(parseInt(hex.slice(index, index + 2), 16) * .09 + 255 * .91))
  return `rgb(${channels.join(',')})`
}
function visible(value: string | null | undefined): string {
  const text = (value ?? '').trim(); if (!text) return ''
  try { const decoded = JSON.parse(text) as unknown; return typeof decoded === 'string' ? decoded : text } catch { return text }
}
</script>

<template>
  <div class="resume-pages" :class="{ 'resume-pages--compact': compact }">
    <article v-for="(pageColumns, pageIndex) in pages" :key="pageIndex" class="resume-sheet"
      :class="[templateClass, `resume-sheet--density-${visual.density?.toLowerCase()}`, { 'resume-sheet--compact': compact }]"
      :style="sheetStyle" :data-renderer-protocol="rendererProtocol" :aria-label="`简历版式预览第 ${pageIndex + 1} 页`">
      <header v-if="pageIndex === 0" class="resume-sheet__head" :class="`resume-sheet__head--${visual.headerStyle?.toLowerCase()}`">
        <div class="resume-sheet__identity"><h2>{{ displayName }}</h2><p v-if="targetJob">{{ targetJob }}</p>
          <small v-if="contactLine">{{ contactLine }}</small><small v-else class="resume-contact-placeholder">{{ visual.subtitle }}</small></div>
        <img v-if="displayPhoto" class="resume-sheet__photo" :src="photoUrl || ''" alt="简历照片" />
        <span v-else-if="visual.showMark" class="resume-sheet__mark">JP</span>
      </header>
      <header v-else class="resume-sheet__continuation"><strong>{{ displayName }}</strong><span>续页 {{ pageIndex + 1 }}</span></header>
      <div class="resume-sheet__rule" />
      <div class="resume-sheet__columns" :style="{ gridTemplateColumns: pageColumns.map((column) => `${column.widthPercent}fr`).join(' ') }">
        <div v-for="column in pageColumns" :key="column.id" class="resume-sheet__column" :class="`resume-sheet__column--${(column.tone || 'plain').toLowerCase()}`">
          <section v-for="section in column.sections" v-show="visible(section.value) || !resume" :key="section.key"
            class="resume-sheet__section" :class="`resume-sheet__section--${section.headingStyle}`"
            :role="interactive ? 'button' : undefined" :tabindex="interactive ? 0 : undefined"
            @click="interactive && emit('sectionClick', section.key)" @keydown.enter="interactive && emit('sectionClick', section.key)">
            <h3>{{ section.label }}</h3>
            <div v-if="section.entries?.length" class="resume-sheet__entries">
              <article v-for="(entry, entryIndex) in section.entries" :key="`${section.key}-${entryIndex}`" class="resume-sheet__entry">
                <div v-if="entry.primary || entry.date" class="resume-sheet__entry-top">
                  <strong>{{ entry.primary }}</strong><time v-if="entry.date">{{ entry.date }}</time>
                </div>
                <div v-if="entry.secondary || entry.location" class="resume-sheet__entry-meta">
                  <span>{{ entry.secondary }}</span><span v-if="entry.location">{{ entry.location }}</span>
                </div>
                <p v-if="entry.description">{{ entry.description }}</p>
                <ul v-if="entry.highlights.length"><li v-for="highlight in entry.highlights" :key="highlight">{{ highlight }}</li></ul>
              </article>
            </div>
            <p v-else-if="visible(section.value)">{{ visible(section.value) }}</p>
            <div v-else class="resume-sheet__placeholder" aria-hidden="true"><span /><span /></div>
          </section>
        </div>
      </div>
      <footer class="resume-sheet__page-number">{{ pageIndex + 1 }} / {{ pages.length }}</footer>
    </article>
  </div>
</template>

<style scoped>
.resume-pages{width:min(100%,690px);margin:0 auto;display:grid;gap:18px}.resume-sheet{--resume-accent:#1f2937;--resume-surface:#f3f5f8;--resume-margin-x:8%;--resume-margin-top:6%;--resume-font-scale:1;--resume-line:1.5;--resume-font:"Microsoft YaHei",Arial,sans-serif;position:relative;width:100%;aspect-ratio:210/297;padding:var(--resume-margin-top) var(--resume-margin-x) 6%;overflow:hidden;background:#fff;border:1px solid #d5dae2;box-shadow:0 10px 28px rgba(16,24,40,.09);color:#253044;font-family:var(--resume-font);container-type:inline-size}.resume-sheet__head{min-height:10%;display:flex;align-items:flex-start;justify-content:space-between;gap:2.03cqw}.resume-sheet__identity{min-width:0;display:grid;gap:.435cqw}.resume-sheet__head h2{margin:0;color:var(--resume-accent);font-size:calc(3.188cqw * var(--resume-font-scale));line-height:1.2;letter-spacing:0}.resume-sheet__head p{margin:0;color:#344054;font-size:calc(1.594cqw * var(--resume-font-scale));font-weight:600}.resume-sheet__head small{color:#667085;font-size:calc(1.159cqw * var(--resume-font-scale))}.resume-sheet__head--band{margin:calc(var(--resume-margin-top) * -1) calc(var(--resume-margin-x) * -1) 0;padding:5% var(--resume-margin-x) 3.8%;background:var(--resume-accent)}.resume-sheet__head--band h2,.resume-sheet__head--band p,.resume-sheet__head--band small{color:#fff}.resume-sheet__head--compact{min-height:7%;align-items:baseline}.resume-sheet__head--compact .resume-sheet__identity{width:100%;grid-template-columns:auto 1fr;align-items:baseline;column-gap:1.739cqw}.resume-sheet__head--compact small{grid-column:1/-1}.resume-sheet__head--minimal{justify-content:center;text-align:center}.resume-sheet__head--minimal .resume-sheet__identity{justify-items:center}.resume-sheet__mark{width:5.217cqw;aspect-ratio:1;flex:0 0 5.217cqw;display:grid;place-items:center;background:var(--resume-accent);color:#fff;font-size:1.449cqw;font-weight:700}.resume-sheet__photo{width:6.957cqw;height:8.696cqw;flex:0 0 6.957cqw;object-fit:cover;border:.29cqw solid #fff;box-shadow:0 0 0 1px #d7dce5}.resume-sheet__continuation{display:flex;justify-content:space-between;color:var(--resume-accent);font-size:1.304cqw}.resume-sheet__rule{height:.29cqw;margin:3% 0;background:var(--resume-accent)}.resume-sheet__head--band+.resume-sheet__rule{height:0;margin:2.5% 0}.resume-sheet__columns{min-height:78%;display:grid;gap:3cqw;align-items:stretch}.resume-sheet__column{min-width:0}.resume-sheet__column--neutral,.resume-sheet__column--accent_soft{padding:3cqw}.resume-sheet__column--neutral{background:#f5f6f8}.resume-sheet__column--accent_soft{background:var(--resume-surface)}.resume-sheet__section{margin-top:3.4%}.resume-sheet__section:first-child{margin-top:0}.resume-sheet__section[role=button]{cursor:pointer;border-radius:4px}.resume-sheet__section[role=button]:hover{outline:1px solid var(--resume-accent);outline-offset:3px}.resume-sheet__section h3{margin:0 0 1.6%;color:var(--resume-accent);font-size:calc(1.522cqw * var(--resume-font-scale));line-height:1.3;letter-spacing:0}.resume-sheet__section p,.resume-sheet__entry{color:#344054;font-size:calc(1.159cqw * var(--resume-font-scale));line-height:var(--resume-line);overflow-wrap:anywhere}.resume-sheet__section>p{margin:0;white-space:pre-line}.resume-sheet__entries{display:grid;gap:1.45cqw}.resume-sheet__entry{margin:0;min-width:0}.resume-sheet__entry-top,.resume-sheet__entry-meta{min-width:0;display:grid;grid-template-columns:minmax(0,1fr) auto;align-items:baseline;gap:1.45cqw}.resume-sheet__entry-top strong{min-width:0;color:#253044;font-weight:700}.resume-sheet__entry-top time,.resume-sheet__entry-meta span:last-child{white-space:nowrap;text-align:right}.resume-sheet__entry-meta{margin-top:.18cqw;color:#667085}.resume-sheet__entry p{margin:.45cqw 0 0;white-space:pre-line}.resume-sheet__entry ul{margin:.45cqw 0 0;padding-left:2.45cqw;display:grid;gap:.18cqw}.resume-sheet__entry li{margin:0;padding:0}.resume-sheet__section--rule h3{padding-bottom:1%;border-bottom:1px solid var(--resume-accent)}.resume-sheet__section--bar h3,.resume-sheet__section--table h3{padding:1.2% 2%;background:var(--resume-surface)}.resume-sheet__section--sideline{padding-left:3%;border-left:.29cqw solid var(--resume-accent)}.resume-sheet__section--table{border:1px solid #d7dce5}.resume-sheet__section--table>p,.resume-sheet__section--table .resume-sheet__entries,.resume-sheet__section--table .resume-sheet__placeholder{padding:2%}.resume-sheet--density-compact .resume-sheet__section{margin-top:2.5%}.resume-sheet--density-airy .resume-sheet__section{margin-top:4.6%}.resume-sheet__placeholder{display:grid;gap:.58cqw}.resume-sheet__placeholder span{height:.435cqw;background:#e4e7ec}.resume-sheet__placeholder span:last-child{width:72%}.resume-sheet__page-number{position:absolute;right:var(--resume-margin-x);bottom:2.5%;color:#98a2b3;font-size:1.014cqw}.resume-sheet--tech-single .resume-sheet__head{padding-bottom:2%;border-bottom:.58cqw solid var(--resume-accent)}.resume-sheet--tech-double .resume-sheet__rule{width:6.087cqw}.resume-sheet--campus .resume-sheet__head--band{background:var(--resume-accent)}.resume-sheet--career-pro .resume-sheet__head h2{text-transform:uppercase}.resume-sheet--consulting .resume-sheet__head{border-top:.725cqw solid var(--resume-accent);padding-top:3%}.resume-sheet--finance .resume-sheet__rule{height:.145cqw}.resume-sheet--product-ops .resume-sheet__head{padding-left:3%;border-left:1.159cqw solid var(--resume-accent)}.resume-sheet--education-research .resume-sheet__head{padding-bottom:3%;border-bottom:.435cqw double var(--resume-accent)}.resume-sheet--education-research .resume-sheet__section{margin-top:2%}.resume-sheet--english-single .resume-sheet__section h3{text-transform:uppercase}.resume-sheet--cn-table .resume-sheet__section{margin-top:0}.resume-sheet--cn-table .resume-sheet__section--table h3{padding:.55% 1.5%}.resume-sheet--cn-table .resume-sheet__section--table>p,.resume-sheet--cn-table .resume-sheet__section--table .resume-sheet__entries,.resume-sheet--cn-table .resume-sheet__section--table .resume-sheet__placeholder{padding:1% 1.5%}.resume-sheet--qa-data .resume-sheet__head{background:var(--resume-surface);padding:3%}.resume-pages--compact{gap:5px}.resume-sheet--compact{box-shadow:none}.resume-sheet--compact:nth-child(n+2){display:none}
</style>

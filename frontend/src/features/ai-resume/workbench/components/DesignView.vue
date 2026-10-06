<script setup lang="ts">
import { computed, ref } from 'vue'
import { Check, ChevronDown, ChevronUp, GripVertical, Palette, RotateCcw } from 'lucide-vue-next'
import UiButton from '@/shared/ui/UiButton.vue'
import UiIconButton from '@/shared/ui/UiIconButton.vue'
import UiSegmented from '@/shared/ui/UiSegmented.vue'
import UiSwitch from '@/shared/ui/UiSwitch.vue'
import { useScrollSurface } from '@/shared/composables/useScrollSurface'
import { useSortableList } from '@/shared/composables/useSortableList'
import { DATE_FORMATS } from '@/resume-render/model/dates'
import type { SectionKey } from '@/resume-render/model/types'
import type { FontPairing, ResumeDesignV2 } from '@/resume-render/theme/design'
import { useWorkbench } from '../useWorkbench'
import { SECTION_TITLE_MAX } from '../useDesignDraft'
import SaveIndicator from './SaveIndicator.vue'

type Choice = { value: string; label: string }
type SegmentKey = 'fontSize' | 'lineHeight' | 'spacing' | 'pageMargin' | 'pageTarget' | 'dateFormat' | 'paperSize'

const FONT_LABELS: Record<FontPairing, { label: string; hint: string }> = {
  sans: { label: '现代黑体', hint: '思源黑体 + Inter' },
  serif: { label: '经典宋体', hint: '思源宋体 + Source Serif' },
  mixed: { label: '宋黑混排', hint: '标题宋体，正文黑体' },
  tech: { label: '技术等宽', hint: '黑体 + JetBrains Mono' },
}
const SIZE: Choice[] = [{ value: 'XS', label: '极小' }, { value: 'S', label: '小' }, { value: 'M', label: '标准' }, { value: 'L', label: '大' }, { value: 'XL', label: '特大' }]
const THREE: Record<string, Choice[]> = {
  lineHeight: [{ value: 'COMPACT', label: '紧凑' }, { value: 'NORMAL', label: '标准' }, { value: 'RELAXED', label: '舒展' }],
  spacing: [{ value: 'TIGHT', label: '紧凑' }, { value: 'NORMAL', label: '标准' }, { value: 'RELAXED', label: '舒展' }],
  pageMargin: [{ value: 'NARROW', label: '窄' }, { value: 'STANDARD', label: '标准' }, { value: 'WIDE', label: '宽' }],
}
const PHOTO_MODES: Choice[] = [{ value: 'AUTO', label: '有照片时显示' }, { value: 'SHOW', label: '显示' }, { value: 'HIDE', label: '隐藏' }]
const PHOTO_SHAPES: Choice[] = [{ value: 'CIRCLE', label: '圆形' }, { value: 'ROUNDED', label: '圆角' }, { value: 'SQUARE', label: '方形' }]
const REGION_LABELS: Record<string, string> = { main: '主栏', side: '侧栏', facts: '侧栏' }

const wb = useWorkbench()
const design = wb.design
const settings = design.activeDesign
const manifest = design.manifest

const scroller = ref<HTMLElement | null>(null)
const sectionList = ref<HTMLElement | null>(null)
const surface = useScrollSurface(scroller)
useSortableList(sectionList, { handle: '.section-row__grip', onMove: (from, to) => design.moveSection(from, to) })

const typography = computed(() => {
  const target = manifest.value
  const rows: Array<{ key: SegmentKey; label: string; items: Choice[] }> = [
    { key: 'fontSize', label: '字号', items: SIZE },
    { key: 'lineHeight', label: '行距', items: THREE.lineHeight! },
    { key: 'spacing', label: '段落间距', items: THREE.spacing! },
    { key: 'pageMargin', label: '页边距', items: THREE.pageMargin! },
    {
      key: 'pageTarget',
      label: '目标页数',
      items: [{ value: 'AUTO', label: `自动（≤${target?.maxPages ?? 2} 页）` }, { value: 'ONE', label: '1 页' }, { value: 'TWO', label: '2 页' }],
    },
  ]
  return rows
})
const details = computed(() => {
  const rows: Array<{ key: SegmentKey; label: string; items: Choice[] }> = [
    { key: 'dateFormat', label: '日期格式', items: DATE_FORMATS.map(value => ({ value, label: value === 'MMM YYYY' ? 'Jan 2024' : value.replace('YYYY', '2024').replace('MM', '03') })) },
  ]
  if ((manifest.value?.paperSizes.length ?? 0) > 1) rows.push({ key: 'paperSize', label: '纸张', items: [{ value: 'A4', label: 'A4' }, { value: 'LETTER', label: 'US Letter' }] })
  return rows
})
const customAccent = computed(() => settings.value?.customAccent ?? '')
const customPickerValue = computed(() => customAccent.value || manifest.value?.palettes[0]?.accent || '#1d4ed8')
const multiRegion = computed(() => (manifest.value?.regions.length ?? 0) > 1)
const regionChoices = computed<Choice[]>(() => (manifest.value?.regions ?? []).map(region => ({ value: region.id, label: REGION_LABELS[region.id] ?? region.id })))
const editing = ref<SectionKey | null>(null)

function value(key: SegmentKey): string {
  return String(settings.value?.[key] ?? '')
}

function set(key: SegmentKey, next: string): void {
  design.update(key, next as never)
}

function choosePalette(id: string): void {
  if (!settings.value) return
  design.update('paletteId', id)
  if (settings.value.customAccent) design.update('customAccent', null)
}

function setCustomAccent(event: Event): void {
  const next = (event.target as HTMLInputElement).value.toLowerCase()
  if (/^#[0-9a-f]{6}$/.test(next)) design.update('customAccent', next)
}

function rename(key: SectionKey, event: Event): void {
  design.renameSection(key, (event.target as HTMLInputElement).value)
  editing.value = null
}

function set2<K extends keyof ResumeDesignV2>(key: K, next: ResumeDesignV2[K]): void {
  design.update(key, next)
}
</script>

<template>
  <div class="design-view scroll-edges" :class="{ 'can-scroll-up': surface.edges.canScrollUp, 'can-scroll-down': surface.edges.canScrollDown }">
    <div ref="scroller" class="design-view__scroll" @scroll.passive="surface.onScroll">
      <section class="design-panel">
        <header class="design-panel__head">
          <div><strong>{{ manifest?.name ?? '当前模板' }} · 设计</strong><span>修改即时预览并自动保存；配色与版式按模板分别记忆</span></div>
          <SaveIndicator :state="design.saveState.value" @retry="design.persist()" />
        </header>

        <p v-if="!settings || !manifest" class="design-panel__empty">正在读取模板设计…</p>

        <template v-else>
          <section class="design-group">
            <h3>配色 <small>强调色对比度不足时会自动加深</small></h3>
            <div class="swatches" role="radiogroup" aria-label="配色">
              <button
                v-for="palette in manifest.palettes"
                :key="palette.id"
                type="button"
                role="radio"
                class="swatch"
                :aria-checked="!settings.customAccent && settings.paletteId === palette.id"
                :aria-label="palette.name"
                :title="palette.name"
                :style="{ '--swatch': palette.accent }"
                @click="choosePalette(palette.id)"
              >
                <Check v-if="!settings.customAccent && settings.paletteId === palette.id" :size="15" :stroke-width="3" aria-hidden="true" />
              </button>
              <label class="swatch swatch--custom" :class="{ 'is-active': Boolean(customAccent) }" :style="customAccent ? { '--swatch': customAccent } : undefined" title="自定义强调色">
                <Check v-if="customAccent" :size="15" :stroke-width="3" aria-hidden="true" />
                <Palette v-else :size="15" aria-hidden="true" />
                <input type="color" :value="customPickerValue" aria-label="自定义强调色" @change="setCustomAccent">
              </label>
            </div>
            <p class="design-hint">{{ settings.customAccent ? `自定义 ${settings.customAccent}` : manifest.palettes.find(item => item.id === settings?.paletteId)?.name }}</p>
          </section>

          <section v-if="manifest.fontPairings.length > 1" class="design-group">
            <h3>字体组合</h3>
            <div class="option-grid" role="radiogroup" aria-label="字体组合">
              <button
                v-for="pairing in manifest.fontPairings"
                :key="pairing"
                type="button"
                role="radio"
                class="option"
                :class="`option--font-${pairing}`"
                :aria-checked="settings.fontPairing === pairing"
                @click="set2('fontPairing', pairing)"
              >
                <strong>{{ FONT_LABELS[pairing].label }}</strong>
                <small>{{ FONT_LABELS[pairing].hint }}</small>
              </button>
            </div>
          </section>

          <section class="design-group">
            <h3>排版</h3>
            <div class="design-rows">
              <div v-for="control in typography" :key="control.key" class="design-row">
                <span>{{ control.label }}</span>
                <UiSegmented :model-value="value(control.key)" :items="control.items" :aria-label="control.label" size="sm" @update:model-value="set(control.key, $event)" />
              </div>
            </div>
          </section>

          <section v-if="manifest.headerVariants.length > 1" class="design-group">
            <h3>页头样式</h3>
            <div class="option-grid" role="radiogroup" aria-label="页头样式">
              <button
                v-for="variant in manifest.headerVariants"
                :key="variant.id"
                type="button"
                role="radio"
                class="option"
                :aria-checked="settings.headerVariant === variant.id"
                @click="set2('headerVariant', variant.id)"
              >
                <strong>{{ variant.name }}</strong>
              </button>
            </div>
          </section>

          <section class="design-group">
            <h3>细节</h3>
            <div class="design-rows">
              <div v-for="control in details" :key="control.key" class="design-row">
                <span>{{ control.label }}</span>
                <UiSegmented :model-value="value(control.key)" :items="control.items" :aria-label="control.label" size="sm" @update:model-value="set(control.key, $event)" />
              </div>
              <template v-if="manifest.photo === 'optional'">
                <div class="design-row">
                  <span>照片</span>
                  <UiSegmented :model-value="settings.photo.mode" :items="PHOTO_MODES" aria-label="照片" size="sm" @update:model-value="design.updatePhoto({ mode: $event as ResumeDesignV2['photo']['mode'] })" />
                </div>
                <div v-if="settings.photo.mode !== 'HIDE'" class="design-row">
                  <span>照片形状</span>
                  <UiSegmented :model-value="settings.photo.shape" :items="PHOTO_SHAPES" aria-label="照片形状" size="sm" @update:model-value="design.updatePhoto({ shape: $event as ResumeDesignV2['photo']['shape'] })" />
                </div>
              </template>
              <div class="design-row">
                <span>联系方式图标</span>
                <UiSwitch :model-value="settings.contactIcons" label="联系方式图标" @update:model-value="set2('contactIcons', $event)" />
              </div>
              <div v-if="manifest.decorations" class="design-row">
                <span>底纹与装饰</span>
                <UiSwitch :model-value="settings.decorations" label="底纹与装饰" @update:model-value="set2('decorations', $event)" />
              </div>
            </div>
          </section>

          <section class="design-group">
            <h3>板块 <small>拖动排序；点击名称可改标题（≤{{ SECTION_TITLE_MAX }} 字）</small></h3>
            <div ref="sectionList" class="section-list">
              <div v-for="(section, index) in design.orderedSections.value" :key="section.key" class="section-row" :class="{ 'is-hidden': !section.visible, 'has-region': multiRegion }">
                <span class="section-row__grip" title="拖动调整顺序" aria-hidden="true"><GripVertical :size="15" /></span>
                <input
                  v-if="editing === section.key"
                  class="section-row__input"
                  :value="section.title"
                  :maxlength="SECTION_TITLE_MAX"
                  :placeholder="section.defaultTitle"
                  :aria-label="`${section.defaultTitle}的标题`"
                  autofocus
                  @blur="rename(section.key, $event)"
                  @keydown.enter.prevent="($event.target as HTMLInputElement).blur()"
                  @keydown.esc.prevent="editing = null"
                >
                <button v-else type="button" class="section-row__label" :title="`重命名${section.title}`" @click="editing = section.key">
                  {{ section.title }}<small v-if="section.title !== section.defaultTitle">原：{{ section.defaultTitle }}</small>
                </button>
                <UiSegmented
                  v-if="multiRegion"
                  class="section-row__region"
                  :model-value="section.region"
                  :items="regionChoices"
                  :aria-label="`${section.title}所在栏`"
                  size="sm"
                  @update:model-value="design.assignRegion(section.key, $event)"
                />
                <UiIconButton :icon="ChevronUp" size="sm" :label="`上移${section.title}`" :disabled="index === 0" @click="design.moveSection(index, index - 1)" />
                <UiIconButton :icon="ChevronDown" size="sm" :label="`下移${section.title}`" :disabled="index === design.orderedSections.value.length - 1" @click="design.moveSection(index, index + 1)" />
                <UiSwitch :model-value="section.visible" :label="`显示${section.title}`" class="section-row__switch" @update:model-value="design.toggleSection(section.key, $event)" />
              </div>
            </div>
          </section>

          <footer class="design-panel__foot">
            <UiButton variant="ghost" size="sm" :icon="RotateCcw" @click="design.resetToDefaults()">恢复模板默认</UiButton>
          </footer>
        </template>
      </section>
    </div>
  </div>
</template>

<style scoped>
.design-view {
  min-height: 0;
  height: 100%;
}

.design-view__scroll {
  height: 100%;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: var(--space-4) var(--space-5) var(--space-8);
}

.design-panel {
  display: grid;
  gap: var(--space-5);
}

.design-panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.design-panel__head > div {
  min-width: 0;
  display: grid;
  gap: 2px;
}

.design-panel__head strong {
  font-size: var(--fs-body);
  font-weight: 700;
}

.design-panel__head span {
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
}

.design-panel__empty {
  color: var(--text-tertiary);
  font-size: var(--fs-sm);
}

.design-group {
  display: grid;
  gap: var(--space-3);
}

.design-group h3 {
  display: flex;
  align-items: baseline;
  gap: var(--space-2);
  color: var(--text-secondary);
  font-size: var(--fs-xs);
  font-weight: 700;
  letter-spacing: 0.04em;
}

.design-group h3 small {
  color: var(--text-tertiary);
  font-weight: 500;
  letter-spacing: 0;
}

.option-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(132px, 1fr));
  gap: var(--space-2);
}

.option {
  min-height: 48px;
  padding: 8px 12px;
  display: grid;
  align-content: center;
  gap: 2px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
  background: var(--surface-1);
  color: var(--text-secondary);
  text-align: left;
  transition: border-color var(--dur-fast), background-color var(--dur-fast), color var(--dur-fast);
}

.option strong {
  font-size: var(--fs-sm);
  font-weight: 650;
}

.option small {
  color: var(--text-tertiary);
  font-size: 11px;
}

.option--font-serif strong,
.option--font-mixed strong {
  font-family: 'Noto Serif SC', 'Source Serif 4', serif;
}

.option--font-tech small {
  font-family: 'JetBrains Mono', ui-monospace, monospace;
}

.option:hover {
  border-color: var(--border-strong);
  color: var(--text-primary);
}

.option[aria-checked='true'] {
  border-color: var(--color-primary);
  background: var(--color-primary-soft);
  color: var(--color-primary-text);
  box-shadow: 0 0 0 1px var(--color-primary);
}

.design-hint {
  margin: 0;
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
}

.design-panel__foot {
  display: flex;
  justify-content: flex-end;
}

.swatches {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
}

.swatch {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: var(--swatch);
  color: var(--text-on-primary);
  box-shadow: 0 0 0 2px var(--surface-1), 0 0 0 3px var(--border-default);
  transition: transform var(--dur-fast) var(--ease-spring), box-shadow var(--dur-fast);
}

.swatch:hover {
  transform: scale(1.08);
}

.swatch[aria-checked='true'] {
  box-shadow: 0 0 0 2px var(--surface-1), 0 0 0 4px var(--swatch);
}

.design-rows {
  display: grid;
  gap: var(--space-2);
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  background: var(--surface-1);
}

.design-row {
  min-height: 36px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.design-row > span {
  color: var(--text-secondary);
  font-size: var(--fs-sm);
  font-weight: 550;
}

.swatch--custom {
  position: relative;
  overflow: hidden;
  background: conic-gradient(from 90deg, red, orange, yellow, lime, cyan, blue, magenta, red);
  cursor: pointer;
}

.swatch--custom.is-active {
  background: var(--swatch);
  box-shadow: 0 0 0 2px var(--surface-1), 0 0 0 4px var(--swatch);
}

.swatch--custom input {
  position: absolute;
  inset: 0;
  opacity: 0;
  cursor: pointer;
}

.swatch--custom:focus-within {
  box-shadow: var(--focus-ring);
}

.option:focus-visible,
.swatch:focus-visible {
  outline: none;
  box-shadow: var(--focus-ring);
}

.section-list {
  display: grid;
  overflow: hidden;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  background: var(--surface-1);
}

.section-row {
  min-height: 44px;
  padding: 0 var(--space-3) 0 var(--space-2);
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto auto auto;
  align-items: center;
  gap: 2px;
  border-bottom: 1px solid var(--border-subtle);
  background: var(--surface-1);
}

.section-row:last-child {
  border-bottom: 0;
}

.section-row.is-hidden .section-row__label {
  color: var(--text-tertiary);
  text-decoration: line-through;
}

.section-row.is-drag-ghost {
  opacity: 0.4;
}

.section-row.is-drag-chosen {
  box-shadow: var(--shadow-lg);
}

.section-row__grip {
  width: 24px;
  height: 30px;
  display: grid;
  place-items: center;
  border-radius: var(--radius-sm);
  color: var(--text-tertiary);
  cursor: grab;
}

.section-row__grip:hover {
  background: var(--surface-2);
}

.section-row.has-region {
  grid-template-columns: auto minmax(0, 1fr) auto auto auto auto;
}

.section-row__label {
  min-width: 0;
  padding: 4px;
  display: flex;
  align-items: baseline;
  gap: 6px;
  border-radius: var(--radius-sm);
  font-size: var(--fs-sm);
  font-weight: 600;
  text-align: left;
}

.section-row__label:hover {
  background: var(--surface-2);
}

.section-row__label small {
  overflow: hidden;
  color: var(--text-tertiary);
  font-size: 11px;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.section-row__input {
  min-width: 0;
  height: 30px;
  padding: 0 8px;
  border: 1px solid var(--color-primary);
  border-radius: var(--radius-sm);
  background: var(--surface-1);
  color: var(--text-primary);
  font-size: var(--fs-sm);
}

.section-row__input:focus {
  outline: none;
  box-shadow: var(--focus-ring);
}

.section-row__region {
  margin-right: var(--space-1);
}

.section-row__switch {
  margin-left: var(--space-2);
}

@media (max-width: 640px) {
  .design-view__scroll {
    padding: var(--space-3);
  }


  .design-row {
    align-items: flex-start;
    flex-direction: column;
    gap: 6px;
  }
}
</style>

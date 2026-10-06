<script setup lang="ts">
import { computed, ref } from 'vue'
import { Check, ChevronDown, ChevronUp, GripVertical } from 'lucide-vue-next'
import UiIconButton from '@/shared/ui/UiIconButton.vue'
import UiSegmented from '@/shared/ui/UiSegmented.vue'
import UiSwitch from '@/shared/ui/UiSwitch.vue'
import { useScrollSurface } from '@/shared/composables/useScrollSurface'
import { useSortableList } from '@/shared/composables/useSortableList'
import type { ResumeDesignSettings } from '@/features/resume/types'
import { useWorkbench } from '../useWorkbench'
import DesignGlyph from './DesignGlyph.vue'
import SaveIndicator from './SaveIndicator.vue'

type SegmentKey = 'fontPreset' | 'fontScale' | 'lineHeight' | 'density' | 'pageMargin' | 'dateFormat' | 'photoMode'
type Choice = { value: string; label: string }

const SPACING: Choice[] = [{ value: 'COMPACT', label: '紧凑' }, { value: 'STANDARD', label: '标准' }, { value: 'AIRY', label: '舒展' }]
const TYPOGRAPHY: Array<{ key: SegmentKey; label: string; items: Choice[] }> = [
  { key: 'fontPreset', label: '字体', items: [{ value: 'MODERN_SANS', label: '现代黑体' }, { value: 'CLASSIC_SERIF', label: '经典宋体' }] },
  { key: 'fontScale', label: '字号', items: [{ value: 'SMALL', label: '小' }, { value: 'STANDARD', label: '标准' }, { value: 'LARGE', label: '大' }] },
  { key: 'lineHeight', label: '行距', items: SPACING },
  { key: 'density', label: '内容密度', items: SPACING },
  { key: 'pageMargin', label: '页边距', items: [{ value: 'NARROW', label: '窄' }, { value: 'STANDARD', label: '标准' }, { value: 'WIDE', label: '宽' }] },
]
const DETAILS: Array<{ key: SegmentKey; label: string; items: Choice[] }> = [
  { key: 'dateFormat', label: '日期格式', items: [{ value: 'YYYY_DOT_MM', label: 'YYYY.MM' }, { value: 'YYYY_CN_MM', label: 'YYYY年MM月' }] },
  { key: 'photoMode', label: '照片', items: [{ value: 'AUTO', label: '自动' }, { value: 'SHOW', label: '显示' }, { value: 'HIDE', label: '隐藏' }] },
]
const HEADER_LAYOUTS: Choice[] = [
  { value: 'MINIMAL', label: '居中简约' }, { value: 'BAND', label: '色带头部' }, { value: 'SPLIT', label: '左右分栏' }, { value: 'COMPACT', label: '紧凑头部' },
]
const HEADING_STYLES: Choice[] = [
  { value: 'RULE', label: '下划线' }, { value: 'BAR', label: '色条' }, { value: 'SIDELINE', label: '侧线' }, { value: 'PLAIN', label: '纯文本' }, { value: 'TABLE', label: '表格' },
]

const wb = useWorkbench()
const design = wb.design
const settings = design.activeDesign
const accent = computed(() => settings.value?.accentColor ?? undefined)

const scroller = ref<HTMLElement | null>(null)
const sectionList = ref<HTMLElement | null>(null)
const surface = useScrollSurface(scroller)
useSortableList(sectionList, { handle: '.section-row__grip', onMove: (from, to) => design.moveSection(from, to) })

function value(key: keyof ResumeDesignSettings): string {
  return String(settings.value?.[key] ?? '')
}

function set(key: keyof ResumeDesignSettings, next: string): void {
  design.update(key, next as never)
}
</script>

<template>
  <div class="design-view scroll-edges" :class="{ 'can-scroll-up': surface.edges.canScrollUp, 'can-scroll-down': surface.edges.canScrollDown }">
    <div ref="scroller" class="design-view__scroll" @scroll.passive="surface.onScroll">
      <section class="design-panel">
        <header class="design-panel__head">
          <div><strong>当前模板设计</strong><span>{{ design.activeTemplate.value?.displayName ?? '读取中' }} · 按模板单独记忆</span></div>
          <SaveIndicator :state="design.saveState.value" @retry="design.persist()" />
        </header>

        <p v-if="!settings" class="design-panel__empty">当前模板没有可调整的设计项。</p>

        <template v-else>
          <section v-if="design.activeTemplate.value?.presets.length" class="design-group">
            <h3>设计预设</h3>
            <div class="preset-grid">
              <button
                v-for="preset in design.activeTemplate.value.presets"
                :key="preset.variantCode"
                type="button"
                class="preset"
                :aria-pressed="design.activeVariant.value === preset.variantCode"
                @click="design.changePreset(preset.variantCode)"
              >
                <span class="preset__swatch" :style="{ background: preset.settings.accentColor }" aria-hidden="true" />
                <span>{{ preset.displayName }}</span>
                <Check v-if="design.activeVariant.value === preset.variantCode" :size="14" aria-hidden="true" />
              </button>
            </div>
          </section>

          <section v-if="design.accentColors.value.length" class="design-group">
            <h3>主题色</h3>
            <div class="swatches" role="radiogroup" aria-label="主题色">
              <button
                v-for="color in design.accentColors.value"
                :key="color"
                type="button"
                role="radio"
                class="swatch"
                :aria-checked="settings.accentColor.toUpperCase() === color"
                :aria-label="color"
                :title="color"
                :style="{ '--swatch': color }"
                @click="design.update('accentColor', color)"
              >
                <Check v-if="settings.accentColor.toUpperCase() === color" :size="15" :stroke-width="3" aria-hidden="true" />
              </button>
            </div>
          </section>

          <section class="design-group">
            <h3>排版</h3>
            <div class="design-rows">
              <div v-for="control in TYPOGRAPHY" :key="control.key" class="design-row">
                <span>{{ control.label }}</span>
                <UiSegmented :model-value="value(control.key)" :items="control.items" :aria-label="control.label" size="sm" @update:model-value="set(control.key, $event)" />
              </div>
            </div>
          </section>

          <section class="design-group">
            <h3>头部布局</h3>
            <div class="tile-grid tile-grid--4" role="radiogroup" aria-label="头部布局">
              <button
                v-for="option in HEADER_LAYOUTS"
                :key="option.value"
                type="button"
                role="radio"
                class="tile"
                :aria-checked="settings.headerLayout === option.value"
                @click="set('headerLayout', option.value)"
              >
                <DesignGlyph kind="header" :value="option.value" :accent="accent" />
                <span>{{ option.label }}</span>
              </button>
            </div>
          </section>

          <section class="design-group">
            <h3>标题样式</h3>
            <div class="tile-grid tile-grid--5" role="radiogroup" aria-label="标题样式">
              <button
                v-for="option in HEADING_STYLES"
                :key="option.value"
                type="button"
                role="radio"
                class="tile"
                :aria-checked="settings.headingStyle === option.value"
                @click="set('headingStyle', option.value)"
              >
                <DesignGlyph kind="heading" :value="option.value" :accent="accent" />
                <span>{{ option.label }}</span>
              </button>
            </div>
          </section>

          <section class="design-group">
            <h3>细节</h3>
            <div class="design-rows">
              <div v-for="control in DETAILS" :key="control.key" class="design-row">
                <span>{{ control.label }}</span>
                <UiSegmented :model-value="value(control.key)" :items="control.items" :aria-label="control.label" size="sm" @update:model-value="set(control.key, $event)" />
              </div>
            </div>
          </section>

          <section class="design-group">
            <h3>模块显示与顺序 <small>拖动排序；隐藏只影响当前模板</small></h3>
            <div ref="sectionList" class="section-list">
              <div v-for="(section, index) in design.orderedSections.value" :key="section.key" class="section-row" :class="{ 'is-hidden': !section.visible }">
                <span class="section-row__grip" title="拖动调整顺序" aria-hidden="true"><GripVertical :size="15" /></span>
                <span class="section-row__label">{{ section.label }}</span>
                <UiIconButton :icon="ChevronUp" size="sm" :label="`上移${section.label}`" :disabled="index === 0" @click="design.moveSection(index, index - 1)" />
                <UiIconButton :icon="ChevronDown" size="sm" :label="`下移${section.label}`" :disabled="index === design.orderedSections.value.length - 1" @click="design.moveSection(index, index + 1)" />
                <UiSwitch :model-value="section.visible" :label="`显示${section.label}`" class="section-row__switch" @update:model-value="design.toggleSection(section.key, $event)" />
              </div>
            </div>
          </section>
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

.preset-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: var(--space-2);
}

.preset {
  height: 40px;
  padding: 0 12px;
  display: flex;
  align-items: center;
  gap: 8px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
  background: var(--surface-1);
  color: var(--text-secondary);
  font-size: var(--fs-sm);
  font-weight: 600;
  transition: border-color var(--dur-fast), background-color var(--dur-fast), color var(--dur-fast);
}

.preset span:nth-child(2) {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.preset__swatch {
  width: 14px;
  height: 14px;
  flex: none;
  border-radius: 50%;
  box-shadow: inset 0 0 0 1px rgb(0 0 0 / 0.12);
}

.preset:hover {
  border-color: var(--border-strong);
  color: var(--text-primary);
}

.preset[aria-pressed='true'] {
  border-color: var(--color-primary-border);
  background: var(--color-primary-soft);
  color: var(--color-primary-text);
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
  color: #fff;
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

.tile-grid {
  display: grid;
  gap: var(--space-2);
}

.tile-grid--4 {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.tile-grid--5 {
  grid-template-columns: repeat(5, minmax(0, 1fr));
}

.tile {
  min-width: 0;
  padding: 8px 8px 6px;
  display: grid;
  gap: 6px;
  justify-items: center;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  background: var(--surface-1);
  color: var(--text-secondary);
  font-size: 11.5px;
  font-weight: 600;
  transition: border-color var(--dur-fast), background-color var(--dur-fast), color var(--dur-fast), transform var(--dur-fast) var(--ease-out);
}

.tile :deep(.design-glyph) {
  padding: 4px;
  border-radius: var(--radius-sm);
  background: #fff;
  box-shadow: inset 0 0 0 1px rgb(0 0 0 / 0.06);
  color: #253044;
}

.tile:hover {
  border-color: var(--border-strong);
  transform: translateY(-1px);
}

.tile[aria-checked='true'] {
  border-color: var(--color-primary);
  background: var(--color-primary-soft);
  color: var(--color-primary-text);
  box-shadow: 0 0 0 1px var(--color-primary);
}

.tile:focus-visible,
.preset:focus-visible,
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

.section-row__label {
  padding-left: 4px;
  font-size: var(--fs-sm);
  font-weight: 600;
}

.section-row__switch {
  margin-left: var(--space-2);
}

@media (max-width: 640px) {
  .design-view__scroll {
    padding: var(--space-3);
  }

  .tile-grid--4,
  .tile-grid--5 {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .design-row {
    align-items: flex-start;
    flex-direction: column;
    gap: 6px;
  }
}
</style>

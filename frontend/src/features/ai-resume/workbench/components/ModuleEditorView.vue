<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { Check, ChevronDown, Minus } from 'lucide-vue-next'
import UiBadge from '@/shared/ui/UiBadge.vue'
import UiButton from '@/shared/ui/UiButton.vue'
import { useScrollSurface } from '@/shared/composables/useScrollSurface'
import { matchesShortcut } from '@/shared/lib/keyboard'
import type { AiResumeCard } from '../../types'
import { cardMeta } from '../cardConfig'
import { draftLabel } from '../copy'
import { useWorkbench } from '../useWorkbench'
import CardFieldsEditor from './CardFieldsEditor.vue'

const wb = useWorkbench()
const guided = wb.guided
const drafts = wb.drafts

const scroller = ref<HTMLElement | null>(null)
const editor = ref<HTMLElement | null>(null)
const located = ref(false)
const surface = useScrollSurface(scroller)

const card = computed(() => guided.selectedCard.value)
const meta = computed(() => card.value ? cardMeta(card.value.cardType) : null)
const issue = computed(() => card.value ? drafts.submitIssue(card.value) : '')
const issueTone = computed(() => card.value && (drafts.dirty.has(card.value.id) || drafts.draftStates[card.value.id] === 'saved') ? 'is-bad' : 'is-todo')
const busy = computed(() => card.value ? drafts.draftStates[card.value.id] === 'saving' : false)
const total = computed(() => guided.editableCards.value.length)
const progress = computed(() => total.value ? Math.round((guided.confirmedCount.value / total.value) * 100) : 0)
const stateLabel = computed(() => card.value ? draftLabel(drafts.draftStates[card.value.id], card.value.status) : '')
const stateTone = computed(() => {
  const state = card.value ? drafts.draftStates[card.value.id] : undefined
  if (state === 'error') return 'danger'
  if (state === 'waiting' || state === 'saving') return 'warning'
  return card.value?.status === 'CONFIRMED' ? 'success' : 'neutral'
})

function tileState(item: AiResumeCard): 'confirmed' | 'skipped' | 'draft' | 'empty' {
  if (drafts.dirty.has(item.id)) return 'draft'
  if (item.status === 'CONFIRMED') return 'confirmed'
  if (item.status === 'SKIPPED') return 'skipped'
  return 'empty'
}

function select(item: AiResumeCard): void {
  guided.select(item)
  void focusEditor()
}

async function focusEditor(): Promise<void> {
  await nextTick()
  const root = editor.value
  if (!root) return
  root.scrollIntoView?.({ block: 'nearest', behavior: 'smooth' })
  const marked = root.querySelector<HTMLElement>('[data-autofocus]')
  const target = marked?.matches('input, textarea, button, [tabindex]') ? marked : marked?.querySelector<HTMLElement>('input, textarea, button')
  ;(target ?? root.querySelector<HTMLElement>('input, textarea'))?.focus({ preventScroll: true })
  located.value = false
  requestAnimationFrame(() => { located.value = true })
}

function onKeydown(event: KeyboardEvent): void {
  if (card.value && matchesShortcut(event, 'mod+enter') && !issue.value && !busy.value) {
    event.preventDefault()
    void wb.confirmCard(card.value)
  }
}

// The editor may mount after the request (view transition), so consume it immediately too.
watch(wb.focusPending, (pending) => {
  if (!pending) return
  wb.focusPending.value = false
  void focusEditor()
}, { immediate: true })
</script>

<template>
  <div class="module-editor scroll-edges" :class="{ 'can-scroll-up': surface.edges.canScrollUp, 'can-scroll-down': surface.edges.canScrollDown }">
    <div ref="scroller" class="module-editor__scroll" tabindex="-1" @scroll.passive="surface.onScroll">
      <section class="module-overview">
        <button type="button" class="module-overview__toggle" :aria-expanded="wb.moduleListOpen.value" aria-controls="module-nav" @click="wb.moduleListOpen.value = !wb.moduleListOpen.value">
          <span class="module-overview__title">
            <strong>简历模块</strong>
            <small>{{ guided.confirmedCount.value }} / {{ total }} 已确认</small>
          </span>
          <span class="module-overview__bar" aria-hidden="true"><b :style="{ width: `${progress}%` }" /></span>
          <ChevronDown class="module-overview__chevron" :class="{ 'is-open': wb.moduleListOpen.value }" :size="16" aria-hidden="true" />
        </button>

        <nav v-show="wb.moduleListOpen.value" id="module-nav" class="card-nav" aria-label="简历模块">
          <button
            v-for="item in guided.editableCards.value"
            :key="item.id"
            type="button"
            class="card-nav__tile"
            :class="[`is-${tileState(item)}`, { 'is-active': card?.id === item.id }]"
            :aria-current="card?.id === item.id ? 'true' : undefined"
            @click="select(item)"
          >
            <component :is="cardMeta(item.cardType).icon" class="card-nav__icon" :size="16" :stroke-width="1.9" aria-hidden="true" />
            <span class="card-nav__label">{{ cardMeta(item.cardType).label }}</span>
            <span class="card-nav__state" :title="draftLabel(drafts.draftStates[item.id], item.status)">
              <Check v-if="tileState(item) === 'confirmed'" :size="13" :stroke-width="2.6" aria-hidden="true" />
              <Minus v-else-if="tileState(item) === 'skipped'" :size="13" aria-hidden="true" />
              <i v-else aria-hidden="true" />
            </span>
          </button>
        </nav>
      </section>

      <section v-if="card && meta" ref="editor" class="card-editor" :class="{ 'is-located': located }" :aria-label="`${meta.label}编辑`" @keydown="onKeydown" @animationend="located = false">
        <header class="card-editor__head">
          <span class="card-editor__icon"><component :is="meta.icon" :size="18" :stroke-width="1.9" aria-hidden="true" /></span>
          <div class="card-editor__title">
            <h2>{{ meta.label }}</h2>
            <p>{{ meta.description }}</p>
          </div>
          <UiBadge :tone="stateTone" size="sm">{{ stateLabel }}</UiBadge>
        </header>

        <div class="card-editor__body">
          <CardFieldsEditor :key="card.id" :card="card" />
        </div>

        <footer class="card-editor__foot">
          <UiButton :pending="busy" :disabled="Boolean(issue)" @click="wb.confirmCard(card)">确认此模块</UiButton>
          <span class="card-editor__hint" :class="issue ? issueTone : ''">{{ issue || '草稿自动保存；确认后才生成正式修订' }}</span>
        </footer>
      </section>
    </div>
  </div>
</template>

<style scoped>
.module-editor {
  min-height: 0;
  height: 100%;
}

.module-editor__scroll {
  height: 100%;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: var(--space-4) var(--space-5) var(--space-6);
  display: grid;
  align-content: start;
  gap: var(--space-4);
}

.module-overview {
  display: grid;
  gap: var(--space-3);
}

.module-overview__toggle {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--space-3);
  padding: 4px 2px;
  border-radius: var(--radius-sm);
  text-align: left;
}

.module-overview__toggle:focus-visible {
  outline: none;
  box-shadow: var(--focus-ring);
}

.module-overview__title {
  display: flex;
  align-items: baseline;
  gap: var(--space-2);
}

.module-overview__title strong {
  font-size: var(--fs-sm);
  font-weight: 700;
}

.module-overview__title small {
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
  font-variant-numeric: tabular-nums;
}

.module-overview__bar {
  height: 4px;
  overflow: hidden;
  border-radius: var(--radius-full);
  background: var(--surface-3);
}

.module-overview__bar b {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--color-success);
  transition: width var(--dur-slower) var(--ease-out);
}

.module-overview__chevron {
  color: var(--text-tertiary);
  transform: rotate(-90deg);
  transition: transform var(--dur-base) var(--ease-out);
}

.module-overview__chevron.is-open {
  transform: none;
}

.card-nav {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(132px, 1fr));
  gap: var(--space-2);
}

.card-nav__tile {
  min-width: 0;
  height: 40px;
  padding: 0 10px;
  display: grid;
  grid-template-columns: 16px minmax(0, 1fr) 16px;
  align-items: center;
  gap: 8px;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  background: var(--surface-1);
  color: var(--text-secondary);
  text-align: left;
  transition: border-color var(--dur-fast), background-color var(--dur-fast), color var(--dur-fast), box-shadow var(--dur-base);
}

.card-nav__tile:hover {
  border-color: var(--border-default);
  color: var(--text-primary);
}

.card-nav__tile.is-active {
  border-color: var(--color-primary-border);
  background: var(--color-primary-soft);
  color: var(--color-primary-text);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--color-primary-soft) 70%, transparent);
}

.card-nav__tile:focus-visible {
  outline: none;
  box-shadow: var(--focus-ring);
}

.card-nav__label {
  overflow: hidden;
  font-size: var(--fs-sm);
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-nav__state {
  width: 16px;
  height: 16px;
  display: grid;
  place-items: center;
  border-radius: 50%;
}

.card-nav__state i {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  border: 1.5px solid var(--border-strong);
}

.card-nav__tile.is-confirmed .card-nav__state {
  background: var(--color-success);
  color: #fff;
}

.card-nav__tile.is-skipped .card-nav__state {
  background: var(--surface-3);
  color: var(--text-tertiary);
}

.card-nav__tile.is-draft .card-nav__state i {
  border: 0;
  background: var(--color-warning);
}

.card-editor {
  scroll-margin-top: var(--space-4);
  overflow: hidden;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-xl);
  background: var(--surface-1);
  box-shadow: var(--shadow-sm);
}

.card-editor.is-located {
  animation: card-located 900ms var(--ease-out);
}

.card-editor__head {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  border-bottom: 1px solid var(--border-subtle);
  background: linear-gradient(to bottom, var(--surface-2), var(--surface-1));
}

.card-editor__icon {
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  border-radius: var(--radius-md);
  background: var(--color-primary-soft);
  color: var(--color-primary-text);
}

.card-editor__title {
  min-width: 0;
  display: grid;
  gap: 1px;
}

.card-editor__title h2 {
  font-size: var(--fs-body);
  font-weight: 700;
}

.card-editor__title p {
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
}

.card-editor__body {
  padding: var(--space-4);
}

.card-editor__foot {
  display: flex;
  flex-direction: row-reverse;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  border-top: 1px solid var(--border-subtle);
  background: var(--surface-2);
}

.card-editor__hint {
  min-width: 0;
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
  line-height: 1.5;
}

.card-editor__hint.is-bad {
  color: var(--color-danger-text);
  font-weight: 600;
}

.card-editor__hint.is-todo {
  color: var(--text-secondary);
  font-weight: 600;
}

@keyframes card-located {
  0% {
    box-shadow: 0 0 0 0 color-mix(in srgb, var(--color-primary) 45%, transparent);
  }
  100% {
    box-shadow: 0 0 0 10px transparent;
  }
}

@media (max-width: 640px) {
  .module-editor__scroll {
    padding: var(--space-3);
  }

  .card-nav {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .card-editor__foot {
    flex-direction: column;
    align-items: stretch;
  }
}

@media (prefers-reduced-motion: reduce) {
  .card-editor.is-located {
    animation: none;
  }
}
</style>

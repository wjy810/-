<script setup lang="ts">
/**
 * The board: one column per stage, cards dragged between columns with the mouse. On a phone (≤640px) the
 * columns would scroll sideways one at a time, so they become a list grouped by stage, and each card has a
 * "移动到…" menu instead of dragging.
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ChevronDown, MoveRight } from 'lucide-vue-next'
import UiDropdownMenu, { type MenuItem } from '@/shared/ui/UiDropdownMenu.vue'
import type { ApplicationStage, JobApplication } from '../types'
import { STAGES } from '../labels'
import { sortForColumn } from '../utils/receipts'
import ApplicationCard from './ApplicationCard.vue'

const props = defineProps<{ items: JobApplication[]; now: Date }>()
const emit = defineEmits<{ open: [id: string]; move: [id: string, stage: ApplicationStage] }>()

const draggingId = ref<string | null>(null)
const overStage = ref<ApplicationStage | null>(null)
/** Ended applications stay folded into a narrow strip until asked for, so the open stages fit. */
const closedOpen = ref(false)
const closedFolded = computed(() => !closedOpen.value && !draggingId.value)

const columns = computed(() => STAGES.map(stage => ({
  ...stage,
  items: sortForColumn(props.items.filter(item => item.stage === stage.value)),
})))
const draggingStage = computed(() => props.items.find(item => item.id === draggingId.value)?.stage ?? null)

const narrow = ref(false)
let media: MediaQueryList | null = null
const syncNarrow = () => { narrow.value = Boolean(media?.matches) }
onMounted(() => {
  media = typeof window.matchMedia === 'function' ? window.matchMedia('(max-width: 640px)') : null
  syncNarrow()
  media?.addEventListener?.('change', syncNarrow)
})
onBeforeUnmount(() => media?.removeEventListener?.('change', syncNarrow))

/** Folded stage groups on a phone: 已结束 starts folded, like its narrow column on the desktop. */
const folded = ref<Set<ApplicationStage>>(new Set(['CLOSED']))
function toggleGroup(stage: ApplicationStage): void {
  const next = new Set(folded.value)
  next.has(stage) ? next.delete(stage) : next.add(stage)
  folded.value = next
}
function moveMenu(item: JobApplication): MenuItem[] {
  return [{ type: 'label', label: '移动到' }, ...STAGES.filter(stage => stage.value !== item.stage)
    .map(stage => ({ label: stage.label, onSelect: () => emit('move', item.id, stage.value) }))]
}

function onDragStart(event: DragEvent, item: JobApplication): void {
  draggingId.value = item.id
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
    event.dataTransfer.setData('text/plain', item.id)
  }
}

function onDragOver(event: DragEvent, stage: ApplicationStage): void {
  if (!draggingId.value || stage === draggingStage.value) return
  event.preventDefault()
  if (event.dataTransfer) event.dataTransfer.dropEffect = 'move'
  overStage.value = stage
}

function onDrop(event: DragEvent, stage: ApplicationStage): void {
  event.preventDefault()
  const id = draggingId.value ?? event.dataTransfer?.getData('text/plain') ?? ''
  const from = props.items.find(item => item.id === id)?.stage
  reset()
  if (id && from && from !== stage) emit('move', id, stage)
}

function reset(): void {
  draggingId.value = null
  overStage.value = null
}
</script>

<template>
  <div v-if="narrow" class="stage-list" aria-label="投递列表（按阶段）">
    <section v-for="column in columns" :key="column.value" class="stage-list__group">
      <button type="button" class="stage-list__head" :aria-expanded="!folded.has(column.value)" @click="toggleGroup(column.value)">
        <span class="board__dot" :class="`is-${column.value.toLowerCase()}`" aria-hidden="true" />
        <strong>{{ column.label }}</strong>
        <span class="board__count">{{ column.items.length }}</span>
        <ChevronDown :size="16" class="stage-list__chevron" :class="{ 'is-folded': folded.has(column.value) }" aria-hidden="true" />
      </button>
      <ul v-if="!folded.has(column.value) && column.items.length" class="stage-list__items">
        <li v-for="item in column.items" :key="item.id">
          <ApplicationCard :application="item" :now="now" @open="emit('open', item.id)" />
          <UiDropdownMenu :items="moveMenu(item)" align="end">
            <button type="button" class="stage-list__move" :aria-label="`把「${item.company}」移动到其他阶段`"><MoveRight :size="15" />移动到…</button>
          </UiDropdownMenu>
        </li>
      </ul>
      <p v-else-if="!folded.has(column.value)" class="board__empty">暂无</p>
    </section>
  </div>
  <div v-else class="board" :class="{ 'is-dragging': draggingId, 'is-closed-open': !closedFolded }" role="list" aria-label="投递看板">
    <section
      v-for="column in columns"
      :key="column.value"
      class="board__column"
      :class="{ 'is-over': overStage === column.value, 'is-source': draggingStage === column.value, 'is-folded': column.value === 'CLOSED' && closedFolded }"
      role="listitem"
      :aria-label="`${column.label}，${column.items.length} 条`"
      @dragover="onDragOver($event, column.value)"
      @dragleave="overStage === column.value && (overStage = null)"
      @drop="onDrop($event, column.value)"
    >
      <button v-if="column.value === 'CLOSED' && closedFolded" type="button" class="board__fold" :aria-label="`展开已结束的 ${column.items.length} 条投递`" @click="closedOpen = true">
        <span class="board__count">{{ column.items.length }}</span>
        <strong>{{ column.label }}</strong>
      </button>
      <template v-else>
      <header class="board__head">
        <span class="board__dot" :class="`is-${column.value.toLowerCase()}`" aria-hidden="true" />
        <strong>{{ column.label }}</strong>
        <span class="board__count">{{ column.items.length }}</span>
        <button v-if="column.value === 'CLOSED' && closedOpen" type="button" class="board__collapse" aria-label="收起已结束" @click="closedOpen = false">收起</button>
      </header>
      <p class="board__hint">{{ column.hint }}</p>
      <TransitionGroup tag="ul" name="board-card" class="board__list">
        <li
          v-for="item in column.items"
          :key="item.id"
          :data-id="item.id"
          :class="{ 'is-lifted': draggingId === item.id }"
          draggable="true"
          @dragstart="onDragStart($event, item)"
          @dragend="reset"
        >
          <ApplicationCard :application="item" :now="now" @open="emit('open', item.id)" />
        </li>
      </TransitionGroup>
      <p v-if="!column.items.length" class="board__empty">{{ draggingId ? '拖到这里' : '暂无' }}</p>
      </template>
    </section>
  </div>
</template>

<style scoped>
.board {
  --closed-column: 60px;
  display: grid;
  grid-template-columns: repeat(5, minmax(196px, 1fr)) var(--closed-column);
  gap: var(--space-3);
  overflow-x: auto;
  padding-bottom: var(--space-2);
  overscroll-behavior-x: contain;
  scroll-snap-type: x proximity;
}

.board__column {
  min-height: 320px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px 10px;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  background: var(--surface-2);
  scroll-snap-align: start;
  transition: border-color var(--dur-fast) var(--ease-out), background-color var(--dur-fast) var(--ease-out);
}

.board.is-closed-open {
  --closed-column: minmax(196px, 1fr);
}

.board__column.is-folded {
  padding: 8px 4px;
  cursor: pointer;
}

.board__fold {
  height: 100%;
  min-height: 240px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 6px 0;
  border: 0;
  color: var(--text-secondary);
  background: none;
  font: inherit;
  font-size: var(--fs-sm);
  cursor: pointer;
}

.board__fold strong {
  writing-mode: vertical-rl;
  letter-spacing: 0.2em;
  font-weight: 650;
}

.board__fold .board__count {
  margin-left: 0;
}

.board__fold:hover {
  color: var(--text-primary);
}

.board__collapse {
  padding: 0 4px;
  border: 0;
  color: var(--text-tertiary);
  background: none;
  font: inherit;
  font-size: 11px;
  cursor: pointer;
}

.board__collapse:hover {
  color: var(--color-primary-text);
}

.board__column.is-over {
  border-color: var(--color-primary);
  border-style: dashed;
  background: var(--color-primary-soft);
}

.board__head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 4px;
  font-size: var(--fs-sm);
}

.board__head strong {
  font-weight: 650;
}

.board__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--text-tertiary);
}

.board__dot.is-applied { background: var(--color-primary); }
.board__dot.is-assessment { background: var(--color-warning); }
.board__dot.is-interview { background: var(--color-accent, var(--color-primary)); }
.board__dot.is-offer { background: var(--color-success); }
.board__dot.is-closed { background: var(--border-strong); }

.board__count {
  margin-left: auto;
  min-width: 22px;
  padding: 0 7px;
  border-radius: var(--radius-full);
  color: var(--text-secondary);
  background: var(--surface-1);
  font-size: 11px;
  font-weight: 600;
  text-align: center;
  font-variant-numeric: tabular-nums;
}

.board__hint {
  padding: 0 4px 4px;
  color: var(--text-tertiary);
  font-size: 11px;
}

.board__list {
  list-style: none;
  display: grid;
  gap: 8px;
}

.board__list li.is-lifted {
  opacity: 0.45;
}

.board__empty {
  flex: 1;
  display: grid;
  place-items: center;
  min-height: 80px;
  border: 1px dashed var(--border-default);
  border-radius: var(--radius-md);
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
}

.board-card-move,
.board-card-enter-active,
.board-card-leave-active {
  transition: transform var(--dur-base) var(--ease-out), opacity var(--dur-base) var(--ease-out);
}

.board-card-enter-from,
.board-card-leave-to {
  opacity: 0;
  transform: translateY(6px) scale(0.98);
}

.board-card-leave-active {
  position: absolute;
}

.stage-list { display: grid; gap: var(--space-2); }

.stage-list__group {
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  background: var(--surface-2);
}

.stage-list__head {
  width: 100%;
  min-height: 48px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 14px;
  border: 0;
  color: var(--text-primary);
  background: transparent;
  font: inherit;
  cursor: pointer;
}

.stage-list__chevron { margin-left: auto; color: var(--text-tertiary); transition: transform 160ms ease; }
.stage-list__chevron.is-folded { transform: rotate(-90deg); }

.stage-list__items { margin: 0; padding: 0 10px 10px; list-style: none; display: grid; gap: 8px; }
.stage-list__items li { display: grid; gap: 6px; }

.stage-list__move {
  justify-self: end;
  min-height: 36px;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 0 12px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
  color: var(--text-secondary);
  background: var(--surface-1);
  font-size: var(--fs-sm);
  cursor: pointer;
}

.stage-list .board__empty { padding: 0 14px 12px; }

@media (prefers-reduced-motion: reduce) {
  .stage-list__chevron { transition: none; }
  .board-card-move,
  .board-card-enter-active,
  .board-card-leave-active { transition: none; }
}
</style>

<script setup lang="ts">
import { computed } from 'vue'
import { Check, ChevronRight } from 'lucide-vue-next'
import UiButton from '@/shared/ui/UiButton.vue'
import { matchesShortcut } from '@/shared/lib/keyboard'
import type { AiResumeCard } from '../../types'
import { cardMeta } from '../cardConfig'
import { draftLabel } from '../copy'
import { useWorkbench } from '../useWorkbench'
import CardFieldsEditor from './CardFieldsEditor.vue'

const props = defineProps<{ card: AiResumeCard }>()

const wb = useWorkbench()
const meta = computed(() => cardMeta(props.card.cardType))
const issue = computed(() => wb.drafts.submitIssue(props.card))
/** Only flag the issue as an error once the user has started editing this card. */
const issueTone = computed(() => wb.drafts.dirty.has(props.card.id) || wb.drafts.draftStates[props.card.id] === 'saved' ? 'is-bad' : 'is-todo')
const busy = computed(() => wb.drafts.draftStates[props.card.id] === 'saving')
const total = computed(() => wb.guided.editableCards.value.length)
const progress = computed(() => total.value ? Math.round((wb.guided.resolvedCount.value / total.value) * 100) : 0)

function onKeydown(event: KeyboardEvent): void {
  if (matchesShortcut(event, 'mod+enter') && !issue.value && !busy.value) {
    event.preventDefault()
    void wb.confirmCard(props.card)
  }
}
</script>

<template>
  <section class="guided-card" :aria-label="`${meta.label}结构化填写`" @keydown="onKeydown">
    <header class="guided-card__head">
      <span class="guided-card__icon"><component :is="meta.icon" :size="18" :stroke-width="1.9" aria-hidden="true" /></span>
      <div class="guided-card__title">
        <strong>{{ meta.label }}</strong>
        <small>{{ meta.description }}</small>
      </div>
      <div class="guided-card__progress" :title="`已完成 ${wb.guided.resolvedCount.value} / ${total} 个模块`">
        <span>{{ wb.guided.resolvedCount.value }} / {{ total }}</span>
        <i><b :style="{ width: `${progress}%` }" /></i>
      </div>
    </header>

    <div class="guided-card__body">
      <CardFieldsEditor :card="card" />
    </div>

    <footer class="guided-card__foot">
      <span class="guided-card__hint" :class="issue ? issueTone : ''">
        {{ issue || `${draftLabel(wb.drafts.draftStates[card.id], card.status)} · 填写会实时投影到右侧，确认后才进入正式版本` }}
      </span>
      <div class="guided-card__actions">
        <UiButton v-if="card.cardType === 'LANGUAGES'" variant="ghost" :icon-right="ChevronRight" :disabled="busy" @click="wb.skipCard(card)">暂时跳过</UiButton>
        <UiButton :icon="Check" :pending="busy" :disabled="Boolean(issue)" @click="wb.confirmCard(card)">确认并继续</UiButton>
      </div>
    </footer>
  </section>
</template>

<style scoped>
.guided-card {
  margin-left: 42px;
  display: grid;
  overflow: hidden;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-xl);
  background: var(--surface-1);
  box-shadow: var(--shadow-md);
  animation: guided-in var(--dur-slower) var(--ease-spring) both;
}

.guided-card__head {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  border-bottom: 1px solid var(--border-subtle);
  background: linear-gradient(to bottom, var(--surface-2), var(--surface-1));
}

.guided-card__icon {
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  border-radius: var(--radius-md);
  background: var(--color-primary-soft);
  color: var(--color-primary-text);
}

.guided-card__title {
  min-width: 0;
  display: grid;
  gap: 1px;
}

.guided-card__title strong {
  font-size: var(--fs-body);
  font-weight: 700;
}

.guided-card__title small {
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
}

.guided-card__progress {
  display: grid;
  justify-items: end;
  gap: 4px;
  color: var(--text-tertiary);
  font-size: 11px;
  font-variant-numeric: tabular-nums;
}

.guided-card__progress i {
  width: 72px;
  height: 4px;
  overflow: hidden;
  border-radius: var(--radius-full);
  background: var(--surface-3);
}

.guided-card__progress b {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--gradient-ai);
  transition: width var(--dur-slower) var(--ease-out);
}

.guided-card__body {
  padding: var(--space-4);
}

.guided-card__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  border-top: 1px solid var(--border-subtle);
  background: var(--surface-2);
}

.guided-card__hint {
  min-width: 0;
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
  line-height: 1.5;
}

.guided-card__hint.is-bad {
  color: var(--color-danger-text);
  font-weight: 600;
}

.guided-card__hint.is-todo {
  color: var(--text-secondary);
  font-weight: 600;
}

.guided-card__actions {
  flex: none;
  display: inline-flex;
  gap: var(--space-2);
}

@keyframes guided-in {
  from {
    opacity: 0;
    transform: translateY(10px) scale(0.985);
  }
}

@media (max-width: 640px) {
  .guided-card {
    margin-left: 0;
  }

  .guided-card__foot {
    flex-direction: column;
    align-items: stretch;
  }

  .guided-card__actions {
    display: grid;
    grid-auto-flow: column;
    grid-auto-columns: 1fr;
  }
}

@media (prefers-reduced-motion: reduce) {
  .guided-card {
    animation: none;
  }
}
</style>

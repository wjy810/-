<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Search } from 'lucide-vue-next'
import AppIcon from '@/shared/ui/AppIcon.vue'
import UiBadge from '@/shared/ui/UiBadge.vue'
import UiBanner from '@/shared/ui/UiBanner.vue'
import UiButton from '@/shared/ui/UiButton.vue'
import UiDrawer from '@/shared/ui/UiDrawer.vue'
import UiEmptyState from '@/shared/ui/UiEmptyState.vue'
import UiInput from '@/shared/ui/UiInput.vue'
import type { IconName } from '@/shared/ui/icons'
import { formatWhen } from '@/shared/lib/datetime'
import type { CareerRecord } from '@/features/career-library/types'
import type { KeyOutcome } from '../types'

const props = withDefaults(
  defineProps<{
    open: boolean
    outcome: KeyOutcome | null
    evidences: CareerRecord[]
    evidenceError?: string
    linking?: boolean
  }>(),
  { evidenceError: '', linking: false },
)

const emit = defineEmits<{
  close: []
  confirm: [evidenceId: string]
}>()

const search = ref('')
const selectedId = ref('')

const TYPE_ICON: Record<string, IconName> = {
  PROJECT: 'folder',
  INTERNSHIP_OR_WORK: 'briefcase',
  PORTFOLIO: 'link',
  CERTIFICATE: 'award',
  COURSE_OR_CONTEST: 'book',
  TEXT_STATEMENT: 'message',
}

type StrengthMeta = { label: string; tone: 'success' | 'accent' | 'neutral' }

const STRENGTH_META: Record<string, StrengthMeta> = {
  STRONG: { label: '强证据', tone: 'success' },
  MEDIUM: { label: '中等证据', tone: 'accent' },
  WEAK: { label: '弱证据', tone: 'neutral' },
}

const filtered = computed(() => {
  const keyword = search.value.trim().toLowerCase()
  if (!keyword) {
    return props.evidences
  }
  return props.evidences.filter((item) =>
    [item.title, item.description ?? '', item.coreOutcome ?? '', typeLabel(item.type)]
      .join('\n')
      .toLowerCase()
      .includes(keyword),
  )
})

function iconOf(type: string): IconName {
  return TYPE_ICON[type] ?? 'file-text'
}

function strengthOf(strength?: string | null): StrengthMeta {
  return (strength ? STRENGTH_META[strength] : undefined) ?? { label: strength || '未评级', tone: 'neutral' }
}

function typeLabel(type: string): string {
  return ({ EDUCATION: '教育经历', EXPERIENCE: '工作经历', PROJECT: '项目经历', ORGANIZATION: '组织经历',
    SKILL: '技能', CERTIFICATE: '证书', HONOR: '荣誉', LANGUAGE: '语言', ACHIEVEMENT: '成果记录' } as Record<string, string>)[type] || '求职资料'
}

function onPick(id: string): void {
  if (props.evidenceError) {
    return
  }
  selectedId.value = id
}

function onConfirm(): void {
  if (!selectedId.value) {
    return
  }
  emit('confirm', selectedId.value)
}

watch(
  () => [props.open, props.outcome?.id] as const,
  () => {
    if (props.open) {
      search.value = ''
      selectedId.value = props.outcome?.evidenceId ?? ''
    }
  },
)
</script>

<template>
  <UiDrawer :open="open" title="关联求职资料" :width="520" @close="emit('close')">
    <div class="linker">
      <section class="current">
        <p class="current__label">当前语句</p>
        <p class="current__text">{{ outcome?.text?.trim() || '（这条成果没有描述）' }}</p>
      </section>

      <UiBanner v-if="evidenceError" tone="danger">
        {{ evidenceError }}资料列表不可用期间不能关联，也不能把空列表当成没有资料。
      </UiBanner>

      <UiInput
        v-model="search"
        :icon="Search"
        type="text"
        placeholder="搜索你的经历、项目或成果记录"
        aria-label="搜索你的经历、项目或成果记录"
      />

      <div class="linker__meta">
        <span class="muted">全部使用中的求职资料</span>
        <span class="fine">每条关键成果关联一条资料 · 已选择 {{ selectedId ? 1 : 0 }} 条</span>
      </div>

      <div v-if="filtered.length" class="ev-list">
        <div
          v-for="item in filtered"
          :key="item.id"
          class="ev-card"
          :class="{ 'is-on': selectedId === item.id }"
          role="button"
          :tabindex="evidenceError ? -1 : 0"
          :aria-pressed="selectedId === item.id"
          :aria-disabled="Boolean(evidenceError)"
          @click="onPick(item.id)"
          @keydown.enter.prevent="onPick(item.id)"
          @keydown.space.prevent="onPick(item.id)"
        >
          <span class="ev-card__check">
            <AppIcon v-if="selectedId === item.id" name="check" :size="13" />
          </span>
          <span class="ev-card__icon"><AppIcon :name="iconOf(item.type)" :size="16" /></span>
          <span class="ev-card__body">
            <span class="ev-card__title">
              {{ item.title }}
              <UiBadge :tone="strengthOf(item.strength).tone">{{ strengthOf(item.strength).label }}</UiBadge>
              <UiBadge v-if="item.id === outcome?.evidenceId" tone="primary">当前已关联</UiBadge>
            </span>
            <span class="ev-card__sub">来源：{{ typeLabel(item.type) }} · {{ formatWhen(item.updatedAt) }}</span>
            <a
              v-if="item.url"
              class="text-link ev-card__url"
              :href="item.url"
              target="_blank"
              rel="noopener noreferrer"
              @click.stop
            >
              查看来源
            </a>
          </span>
        </div>
      </div>
      <UiEmptyState
        v-else
        :title="evidenceError ? '资料列表不可用' : search ? '没有匹配的资料' : '还没有可用资料'"
        description="可先去「求职资料库」添加项目、经历或成果，再回到这里关联。"
        illustration="empty-folder"
        size="sm"
      />

      <UiBanner tone="warning">资料关联不会自动改写简历内容；冻结时才会登记有效引用。</UiBanner>
    </div>

    <template #footer>
      <UiButton variant="secondary" :disabled="linking" @click="emit('close')">取消</UiButton>
      <UiButton :disabled="!selectedId || Boolean(evidenceError)" :pending="linking" @click="onConfirm">
        确认关联
      </UiButton>
    </template>
  </UiDrawer>
</template>

<style scoped>
.linker {
  display: grid;
  gap: 14px;
  align-content: start;
}

.current {
  background: var(--surface-2);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
  padding: 10px 12px;
}

.current__label {
  font-size: 12px;
  color: var(--text-tertiary);
  margin-bottom: 4px;
}

.current__text {
  font-size: 13px;
  color: var(--text-primary);
  line-height: 1.6;
}

.linker__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.ev-list {
  display: grid;
  gap: 10px;
}

.ev-card {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  width: 100%;
  background: var(--surface-1);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-lg);
  padding: 12px;
  cursor: pointer;
  transition: border-color var(--dur-fast) var(--ease-standard), box-shadow var(--dur-fast) var(--ease-standard);
}

.ev-card:hover {
  border-color: var(--color-primary);
}

.ev-card.is-on {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--color-primary) 12%, transparent);
}

.ev-card[aria-disabled='true'] {
  opacity: 0.6;
  cursor: not-allowed;
}

.ev-card__check {
  width: 18px;
  height: 18px;
  border-radius: 5px;
  border: 1px solid var(--border-strong);
  display: grid;
  place-items: center;
  flex-shrink: 0;
  margin-top: 2px;
  background: var(--surface-1);
  color: var(--text-on-primary);
}

.ev-card.is-on .ev-card__check {
  background: var(--color-primary);
  border-color: var(--color-primary);
}

.ev-card__icon {
  width: 30px;
  height: 30px;
  border-radius: 8px;
  background: var(--color-primary-soft);
  color: var(--color-primary);
  display: grid;
  place-items: center;
  flex-shrink: 0;
}

.ev-card__body {
  display: grid;
  gap: 3px;
  min-width: 0;
  flex: 1;
}

.ev-card__title {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary);
}

.ev-card__sub {
  font-size: 12px;
  color: var(--text-tertiary);
}

.ev-card__url {
  width: fit-content;
}
</style>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, Download, GitBranch, History, MoreHorizontal, PenLine, ShieldCheck, Sparkles } from 'lucide-vue-next'
import UiBadge from '@/shared/ui/UiBadge.vue'
import UiButton from '@/shared/ui/UiButton.vue'
import UiDropdownMenu, { type MenuItem } from '@/shared/ui/UiDropdownMenu.vue'
import UiIconButton from '@/shared/ui/UiIconButton.vue'
import UiTooltip from '@/shared/ui/UiTooltip.vue'
import { useWorkbench } from '../useWorkbench'
import SaveIndicator from './SaveIndicator.vue'

const wb = useWorkbench()
const router = useRouter()
const conversation = wb.session.conversation

const title = computed(() => conversation.value?.resume.title || 'AI 简历工作台')
const manualPath = computed(() => conversation.value ? `/resumes/${conversation.value.masterId}/manual` : '/resumes')
const quota = wb.session.quota
const quotaLow = computed(() => Boolean(quota.value && quota.value.grantedUnits > 0 && quota.value.remainingUnits / quota.value.grantedUnits < 0.15))

const moreItems = computed<MenuItem[]>(() => [
  { label: '简历分支', icon: GitBranch, onSelect: () => void wb.tools.open('branches') },
  { label: '版本历史', icon: History, onSelect: () => void wb.tools.open('history') },
  { label: 'AI 隐私与偏好', icon: ShieldCheck, onSelect: () => void wb.tools.open('privacy') },
  { type: 'separator' },
  { label: '手动编辑', icon: PenLine, onSelect: () => void router.push(manualPath.value) },
])
</script>

<template>
  <header class="wb-header">
    <UiIconButton :icon="ArrowLeft" label="返回简历列表" to="/resumes" tooltip-side="right" />
    <div class="wb-header__title">
      <h1 :title="title">{{ title }}</h1>
      <div v-if="conversation" class="wb-header__meta">
        <UiBadge :tone="wb.session.ready.value ? 'success' : 'neutral'" size="sm" dot>
          {{ wb.session.ready.value ? '第一版已就绪' : '正在收集事实' }}
        </UiBadge>
        <span class="wb-header__version">v{{ conversation.resume.version }}</span>
        <SaveIndicator :state="wb.saveState.value" @retry="wb.saveNow()" />
      </div>
    </div>

    <div v-if="conversation" class="wb-header__tools">
      <UiTooltip v-if="quota" content="本月剩余 AI 次数">
        <span class="wb-quota" :class="{ 'is-low': quotaLow }" tabindex="0">
          <Sparkles :size="14" aria-hidden="true" />
          <span>{{ quota.remainingUnits }}<small> / {{ quota.grantedUnits }}</small></span>
        </span>
      </UiTooltip>
      <div class="wb-header__icons">
        <UiIconButton :icon="GitBranch" label="简历分支" @click="wb.tools.open('branches')" />
        <UiIconButton :icon="History" label="版本历史" @click="wb.tools.open('history')" />
        <UiIconButton :icon="ShieldCheck" label="AI 隐私与偏好" @click="wb.tools.open('privacy')" />
      </div>
      <UiButton class="wb-header__manual" variant="secondary" size="sm" :icon="PenLine" :to="manualPath">手动编辑</UiButton>
      <UiDropdownMenu :items="moreItems" align="end">
        <button class="wb-header__more" type="button" aria-label="更多工具"><MoreHorizontal :size="18" aria-hidden="true" /></button>
      </UiDropdownMenu>
      <UiTooltip content="导出 PDF" :shortcut="['mod', 'E']">
        <UiButton data-testid="export-pdf" size="sm" :icon="Download" :pending="wb.pdf.pending.value" @click="wb.pdf.show()">
          {{ wb.pdf.pending.value ? '正在生成' : '导出 PDF' }}
        </UiButton>
      </UiTooltip>
    </div>
  </header>
</template>

<style scoped>
.wb-header {
  height: 60px;
  padding: 0 var(--space-4) 0 var(--space-3);
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--space-3);
  border-bottom: 1px solid var(--border-subtle);
  background: var(--surface-1);
}

.wb-header__title {
  min-width: 0;
  display: grid;
  gap: 2px;
}

.wb-header__title h1 {
  overflow: hidden;
  font-size: 15px;
  font-weight: 680;
  line-height: 1.3;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.wb-header__meta {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.wb-header__version {
  color: var(--text-tertiary);
  font-family: var(--font-mono);
  font-size: 11px;
}

.wb-header__tools {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.wb-header__icons {
  display: flex;
  align-items: center;
  gap: 2px;
  padding-right: var(--space-1);
  margin-right: var(--space-1);
  border-right: 1px solid var(--border-subtle);
}

.wb-quota {
  height: 30px;
  padding: 0 10px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border-radius: var(--radius-full);
  background: var(--gradient-ai-soft);
  color: var(--color-primary-text);
  font-size: var(--fs-xs);
  font-weight: 650;
  font-variant-numeric: tabular-nums;
}

.wb-quota small {
  color: var(--text-tertiary);
  font-size: 11px;
  font-weight: 500;
}

.wb-quota.is-low {
  background: var(--color-warning-soft);
  color: var(--color-warning-text);
}

.wb-quota:focus-visible {
  outline: none;
  box-shadow: var(--focus-ring);
}

.wb-header__more {
  width: var(--control-md);
  height: var(--control-md);
  display: none;
  place-items: center;
  border-radius: var(--radius-md);
  color: var(--text-secondary);
}

.wb-header__more:hover,
.wb-header__more[data-state='open'] {
  background: var(--surface-2);
  color: var(--text-primary);
}

.wb-header__more:focus-visible {
  outline: none;
  box-shadow: var(--focus-ring);
}

@media (max-width: 1100px) {
  .wb-header__icons,
  .wb-header__manual {
    display: none;
  }

  .wb-header__more {
    display: inline-grid;
  }
}

@media (max-width: 640px) {
  .wb-header {
    height: 56px;
    padding: 0 var(--space-2);
    gap: var(--space-2);
  }

  .wb-quota,
  .wb-header__version,
  .wb-header__meta :deep(.ui-badge) {
    display: none;
  }
}
</style>

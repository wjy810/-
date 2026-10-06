<script setup lang="ts">
import { computed } from 'vue'
import { ArrowRight, GitBranch, LoaderCircle, Plus, RotateCcw, Trash2 } from 'lucide-vue-next'
import UiBadge from '@/shared/ui/UiBadge.vue'
import UiButton from '@/shared/ui/UiButton.vue'
import UiDrawer from '@/shared/ui/UiDrawer.vue'
import UiEmptyState from '@/shared/ui/UiEmptyState.vue'
import UiSelect from '@/shared/ui/UiSelect.vue'
import { formatRelative } from '@/shared/lib/datetime'
import type { AiWritingStyleCode } from '../../types'
import { branchStatusLabel, branchTypeLabel, languageLabel, revisionSourceLabel, revisionSummary, translationStatus, unconfirmedProperNames } from '../copy'
import { useWorkbench } from '../useWorkbench'
import { WRITING_STYLE_OPTIONS } from '../useWorkbenchTools'

const wb = useWorkbench()
const tools = wb.tools
const session = wb.session
const conversation = session.conversation

const meta = computed(() => {
  if (tools.active.value === 'branches') return { title: '简历分支', description: '基础版本的更新只在你确认后同步到子分支' }
  if (tools.active.value === 'history') return { title: '版本历史', description: '每次确认都生成不可变修订；恢复会生成新修订，不删除历史' }
  return { title: 'AI 隐私与偏好', description: '授权、写作风格与历史正文' }
})
const loading = computed(() => tools.pending.value === 'load')
</script>

<template>
  <UiDrawer :open="tools.active.value !== null" :title="meta.title" :description="meta.description" :width="460" @close="tools.close()">
    <div class="tool-drawer" :aria-busy="loading">
      <p v-if="tools.error.value" class="tool-drawer__error" role="alert">{{ tools.error.value }}</p>
      <div v-if="loading" class="tool-drawer__loading"><LoaderCircle :size="20" aria-hidden="true" />正在读取…</div>

      <template v-else-if="tools.active.value === 'branches'">
        <section class="tool-card tool-card--accent">
          <div>
            <h3>创建语言分支</h3>
            <p>从当前确认版本建立另一语言版本，原内容保持不变；译文需要你逐项确认。</p>
          </div>
          <UiButton variant="secondary" size="sm" :icon="Plus" :pending="tools.pending.value === 'branch-create'" @click="tools.createLanguageBranch()">建立语言分支</UiButton>
        </section>

        <ul class="branch-list">
          <li v-for="branch in tools.branches.value" :key="branch.id" class="branch" :class="{ 'is-active': branch.active }">
            <div class="branch__main">
              <span class="branch__type"><GitBranch :size="12" aria-hidden="true" />{{ branchTypeLabel(branch) }} · {{ languageLabel(branch.languageCode) }}</span>
              <strong>{{ branch.title }}</strong>
              <small>{{ branchStatusLabel(branch, Boolean(conversation?.aiAvailable)) }}</small>
              <small v-if="unconfirmedProperNames(branch).length" class="branch__names">待确认专有名称：{{ unconfirmedProperNames(branch).join('、') }}</small>
            </div>
            <div class="branch__actions">
              <UiBadge v-if="branch.active" tone="primary" size="sm">当前</UiBadge>
              <UiButton v-else variant="ghost" size="sm" :pending="tools.pending.value === `switch-${branch.id}`" @click="tools.activateBranch(branch)">切换</UiButton>
              <UiButton v-if="branch.parentBranchId" variant="ghost" size="sm" :pending="tools.pending.value === `diff-${branch.id}`" @click="tools.inspectBranch(branch)">差异</UiButton>
              <UiButton v-if="branch.syncRequired" size="sm" :pending="tools.pending.value === `sync-${branch.id}`" @click="tools.syncBranch(branch)">确认同步</UiButton>
              <UiButton
                v-if="branch.branchType === 'LANGUAGE' && !branch.syncRequired && translationStatus(branch) === 'NOT_STARTED' && session.aiUsable.value"
                variant="ai"
                size="sm"
                :pending="tools.pending.value === `translate-${branch.id}`"
                @click="tools.translateBranch(branch)"
              >翻译</UiButton>
              <UiButton v-if="translationStatus(branch) === 'AWAITING_CONFIRMATION'" size="sm" :pending="tools.pending.value === `confirm-translation-${branch.id}`" @click="tools.confirmTranslation(branch)">确认译文</UiButton>
            </div>
          </li>
        </ul>

        <section v-if="tools.branchDiff.value" class="diff">
          <header><strong>字段差异</strong><span>{{ tools.branchDiff.value.changes.length }} 项</span></header>
          <p v-if="!tools.branchDiff.value.changes.length" class="diff__empty">当前没有字段差异。</p>
          <dl v-else>
            <div v-for="change in tools.branchDiff.value.changes" :key="change.path">
              <dt>{{ change.path }}</dt>
              <dd>
                <span>{{ String(change.before ?? '空').slice(0, 100) }}</span>
                <ArrowRight :size="13" aria-hidden="true" />
                <strong>{{ String(change.after ?? '空').slice(0, 100) }}</strong>
              </dd>
            </div>
          </dl>
        </section>
      </template>

      <template v-else-if="tools.active.value === 'history'">
        <UiEmptyState v-if="!tools.revisions.value.length" title="还没有修订历史" description="确认第一张卡片后，这里会记录每一个正式版本。" illustration="empty-search" size="sm" />
        <ol v-else class="revision-list">
          <li v-for="revision in tools.revisions.value" :key="revision.id" class="revision">
            <span class="revision__dot" aria-hidden="true" />
            <div class="revision__main">
              <span class="revision__no">第 {{ revision.revisionNo }} 版 · {{ revisionSourceLabel(revision.source) }}</span>
              <strong>{{ revisionSummary(revision.content) }}</strong>
              <small :title="new Date(revision.createdAt).toLocaleString('zh-CN')">{{ formatRelative(revision.createdAt) }}</small>
            </div>
            <UiButton
              variant="ghost"
              size="sm"
              :icon="RotateCcw"
              :pending="tools.pending.value === `restore-${revision.id}`"
              :disabled="revision.branchId !== conversation?.activeBranchId"
              @click="tools.restoreRevision(revision)"
            >恢复</UiButton>
          </li>
        </ol>
      </template>

      <template v-else-if="tools.active.value === 'privacy'">
        <section class="tool-card">
          <div>
            <h3>写作风格</h3>
            <p>仅使用受控风格，不允许任意提示词覆盖事实规则。</p>
          </div>
          <UiSelect
            :model-value="tools.writingStyle.value"
            aria-label="写作风格"
            :options="WRITING_STYLE_OPTIONS"
            :disabled="tools.pending.value === 'privacy-style'"
            @update:model-value="tools.updateWritingStyle($event as AiWritingStyleCode)"
          />
        </section>
        <section class="tool-card tool-card--row">
          <div>
            <h3>AI 授权</h3>
            <p>{{ conversation?.consent.status === 'GRANTED' ? '已授权：只发送已确认事实和当前问题，照片不会发送给模型。' : '尚未授权，AI 不会读取你的资料。' }}</p>
          </div>
          <UiButton v-if="conversation?.consent.status === 'GRANTED'" variant="secondary" size="sm" :pending="tools.pending.value === 'privacy-consent'" @click="tools.revokeConsent()">撤销授权</UiButton>
          <UiButton v-else size="sm" :pending="session.consentPending.value" @click="session.grantConsent()">授权使用</UiButton>
        </section>
        <section class="tool-card tool-card--danger">
          <div>
            <h3>AI 历史正文</h3>
            <p>清除全部消息正文和未决 AI 修改，保留正式简历和不含正文的最小审计标记。</p>
          </div>
          <UiButton variant="danger" size="sm" :icon="Trash2" :pending="tools.pending.value === 'privacy-delete'" @click="tools.deleteHistory()">清除 AI 历史</UiButton>
        </section>
      </template>
    </div>
  </UiDrawer>
</template>

<style scoped>
.tool-drawer {
  display: grid;
  gap: var(--space-4);
}

.tool-drawer__error {
  padding: 10px 12px;
  border-radius: var(--radius-md);
  background: var(--color-danger-soft);
  color: var(--color-danger-text);
  font-size: var(--fs-sm);
}

.tool-drawer__loading {
  min-height: 140px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: var(--text-tertiary);
  font-size: var(--fs-sm);
}

.tool-drawer__loading svg {
  animation: tool-spin 0.9s linear infinite;
}

.tool-card {
  display: grid;
  gap: var(--space-3);
  padding: var(--space-4);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  background: var(--surface-1);
}

.tool-card--row {
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
}

.tool-card--accent {
  background: var(--gradient-ai-soft);
  border-color: var(--color-primary-border);
}

.tool-card--danger {
  border-color: color-mix(in srgb, var(--color-danger) 30%, var(--border-subtle));
}

.tool-card > div {
  display: grid;
  gap: 4px;
}

.tool-card h3 {
  font-size: var(--fs-sm);
  font-weight: 700;
}

.tool-card p {
  color: var(--text-secondary);
  font-size: var(--fs-xs);
  line-height: 1.6;
}

.tool-card :deep(.ui-btn) {
  justify-self: start;
}

.branch-list,
.revision-list {
  display: grid;
  margin: 0;
  padding: 0;
  list-style: none;
}

.branch {
  display: grid;
  gap: var(--space-2);
  padding: var(--space-3) var(--space-2) var(--space-3) var(--space-3);
  border-bottom: 1px solid var(--border-subtle);
}

.branch.is-active {
  box-shadow: inset 3px 0 0 var(--color-primary);
}

.branch__main {
  min-width: 0;
  display: grid;
  gap: 2px;
}

.branch__type {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--color-primary-text);
  font-size: 11px;
  font-weight: 650;
}

.branch__main strong {
  font-size: var(--fs-sm);
  overflow-wrap: anywhere;
}

.branch__main small {
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
}

.branch__main .branch__names {
  color: var(--color-warning-text);
}

.branch__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
}

.diff {
  display: grid;
  gap: var(--space-2);
}

.diff header {
  display: flex;
  justify-content: space-between;
  font-size: var(--fs-sm);
}

.diff header span,
.diff__empty {
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
}

.diff dl {
  display: grid;
  margin: 0;
}

.diff dl > div {
  display: grid;
  gap: 4px;
  padding: var(--space-2) 0;
  border-top: 1px solid var(--border-subtle);
}

.diff dt {
  color: var(--text-tertiary);
  font-family: var(--font-mono);
  font-size: 11px;
}

.diff dd {
  margin: 0;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 14px minmax(0, 1fr);
  align-items: center;
  gap: 6px;
  font-size: var(--fs-xs);
}

.diff dd span {
  color: var(--text-tertiary);
  text-decoration: line-through;
  overflow-wrap: anywhere;
}

.diff dd strong {
  color: var(--color-success-text);
  overflow-wrap: anywhere;
}

.revision {
  position: relative;
  display: grid;
  grid-template-columns: 14px minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) 0;
}

.revision::before {
  content: '';
  position: absolute;
  top: 0;
  bottom: 0;
  left: 6px;
  width: 2px;
  background: var(--border-subtle);
}

.revision:first-child::before {
  top: 50%;
}

.revision:last-child::before {
  bottom: 50%;
}

.revision__dot {
  position: relative;
  width: 14px;
  height: 14px;
  border: 3px solid var(--surface-1);
  border-radius: 50%;
  background: var(--color-primary);
  box-shadow: 0 0 0 1px var(--color-primary-border);
}

.revision__main {
  min-width: 0;
  display: grid;
  gap: 2px;
}

.revision__no {
  color: var(--color-primary-text);
  font-size: 11px;
  font-weight: 650;
}

.revision__main strong {
  overflow: hidden;
  font-size: var(--fs-sm);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.revision__main small {
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
}

@keyframes tool-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>

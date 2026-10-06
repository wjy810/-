<script setup lang="ts">
import { reactive, ref } from 'vue'
import AppIcon from '@/shared/ui/AppIcon.vue'
import { errorMessage } from '@/shared/api/types'
import { decideAiResumeChange, undoAiResumeChange } from '../services/aiResumeApi'
import type { AiResumeChangeItem, AiResumeChangeSet } from '../types'

const props = defineProps<{ conversationId: string; changeSet: AiResumeChangeSet }>()
const emit = defineEmits<{ updated: [value: AiResumeChangeSet] }>()
const pendingId = ref('')
const panelError = ref('')
const editing = reactive<Record<string, boolean>>({})
const drafts = reactive<Record<string, string>>({})

const labels: Record<string, string> = {
  SUMMARY: '个人简介', EDUCATION: '教育经历', EXPERIENCE: '工作经历', PROJECTS: '项目经历',
  ORGANIZATIONS: '组织经历', SKILLS: '专业技能', CERTIFICATES: '证书说明', HONORS: '荣誉说明',
  LANGUAGES: '语言能力',
}

const SOURCE_LABELS: Record<string, string> = {
  summary: '个人简介', education: '教育经历', experiences: '工作经历', projects: '项目经历',
  organizations: '组织经历', skills: '专业技能', certificates: '证书', honors: '荣誉', languages: '语言能力',
  intentions: '求职意向', basics: '基本信息', evidence: '资料库',
}

/** Fact sources are keys such as `experiences/0/description`; show the module they came from. */
function sourceLabel(source: string): string {
  const head = source.replace(/^[/.]+/, '').split(/[/.[]/)[0] ?? ''
  return SOURCE_LABELS[head] ?? '简历内容'
}

function text(value: unknown): string {
  return typeof value === 'string' ? value : value == null ? '' : String(value)
}

function startEdit(item: AiResumeChangeItem): void {
  drafts[item.id] = text(item.correctedValue ?? item.proposedValue)
  editing[item.id] = true
}

async function decide(item: AiResumeChangeItem, decision: 'APPLY' | 'REJECT'): Promise<void> {
  pendingId.value = item.id
  panelError.value = ''
  try {
    const edited = editing[item.id] ? drafts[item.id]?.trim() : undefined
    const next = await decideAiResumeChange(
      props.conversationId, props.changeSet.id, item.id, decision, item.version, edited,
    )
    editing[item.id] = false
    emit('updated', next)
  } catch (reason) {
    panelError.value = errorMessage(reason, '修改处理失败，正式简历没有变化')
  } finally {
    pendingId.value = ''
  }
}

async function undo(item: AiResumeChangeItem): Promise<void> {
  pendingId.value = item.id
  panelError.value = ''
  try {
    emit('updated', await undoAiResumeChange(
      props.conversationId, props.changeSet.id, item.id, item.version,
    ))
  } catch (reason) {
    panelError.value = errorMessage(reason, '无法自动撤销，请使用版本历史比较')
  } finally {
    pendingId.value = ''
  }
}

function statusLabel(status: string): string {
  return ({ PENDING: '待确认', APPLIED: '已写入', REJECTED: '已拒绝', STALE: '内容已变化', UNDONE: '已撤销' } as Record<string, string>)[status] ?? status
}
</script>

<template>
  <section class="change-set" aria-label="AI 待确认修改">
    <header>
      <div><AppIcon name="sparkles" :size="15" /><strong>待确认修改</strong></div>
      <span>{{ changeSet.items.filter((item) => item.status === 'PENDING').length }} 项待处理</span>
    </header>
    <p v-if="panelError" class="change-error">{{ panelError }}</p>
    <div class="change-list">
      <article v-for="item in changeSet.items" :key="item.id" class="change-item" :class="`is-${item.status.toLowerCase()}`">
        <div class="change-item__head">
          <div><span>{{ labels[item.module] ?? item.module }}</span><small>{{ statusLabel(item.status) }}</small></div>
          <div v-if="item.status === 'PENDING'" class="change-actions">
            <button type="button" title="编辑建议" :disabled="pendingId === item.id" @click="startEdit(item)"><AppIcon name="edit" :size="15" /></button>
            <button class="accept" type="button" title="接受并写入简历" :disabled="pendingId === item.id" @click="decide(item, 'APPLY')"><AppIcon :name="pendingId === item.id ? 'loader' : 'check'" :size="16" /></button>
            <button class="reject" type="button" title="拒绝这条修改" :disabled="pendingId === item.id" @click="decide(item, 'REJECT')"><AppIcon name="x" :size="16" /></button>
          </div>
          <button v-else-if="item.status === 'APPLIED'" class="undo" type="button" title="撤销这条修改" :disabled="pendingId === item.id" @click="undo(item)"><AppIcon :name="pendingId === item.id ? 'loader' : 'refresh'" :size="14" />撤销</button>
        </div>
        <div v-if="editing[item.id] && item.status === 'PENDING'" class="change-edit">
          <textarea v-model="drafts[item.id]" rows="5" aria-label="编辑建议值" />
          <div><button type="button" @click="editing[item.id] = false">取消</button><button type="button" @click="decide(item, 'APPLY')">确认写入</button></div>
        </div>
        <div v-else class="change-compare">
          <div><span>修改前</span><p>{{ text(item.beforeValue) || '空' }}</p></div>
          <AppIcon name="arrow-right" :size="14" />
          <div><span>{{ item.status === 'APPLIED' ? '已写入' : '建议值' }}</span><p>{{ text(item.correctedValue ?? item.proposedValue) }}</p></div>
        </div>
        <p class="change-reason">{{ item.reason }}</p>
        <details v-if="item.sourceFacts?.length"><summary>查看事实来源</summary><p v-for="source in item.sourceFacts" :key="`${source.source}-${source.quote}`"><span>{{ sourceLabel(source.source) }}</span>{{ source.quote }}</p></details>
      </article>
    </div>
  </section>
</template>

<style scoped>
.change-set { width: calc(100% - 41px); margin-left: 41px; overflow: hidden; border: 1px solid var(--border-default); border-left: 3px solid var(--color-primary); border-radius: 7px; background: var(--surface-1); box-shadow: 0 8px 24px rgba(15, 23, 42, .06); }
.change-set > header { min-height: 46px; padding: 10px 12px; display: flex; align-items: center; justify-content: space-between; gap: 12px; background: var(--surface-2); border-bottom: 1px solid var(--border-subtle); }
.change-set > header div { display: inline-flex; align-items: center; gap: 7px; color: var(--color-primary-text); }
.change-set > header span { color: var(--text-secondary); font-size: 12px; }
.change-list { display: grid; }
.change-item { padding: 13px; display: grid; gap: 10px; border-bottom: 1px solid var(--border-subtle); }
.change-item:last-child { border-bottom: 0; }
.change-item.is-rejected, .change-item.is-undone { opacity: .68; }
.change-item.is-stale { border-left: 3px solid var(--color-warning); }
.change-item__head, .change-item__head > div { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.change-item__head > div:first-child { justify-content: flex-start; }
.change-item__head span { font-size: 12px; font-weight: 700; }
.change-item__head small { padding: 2px 6px; color: var(--color-primary-text); background: var(--surface-2); border-radius: 4px; font-size: 11px; }
.change-actions { flex: none; }
.change-actions button, .undo { min-width: 30px; height: 30px; padding: 0 8px; display: inline-flex; align-items: center; justify-content: center; gap: 5px; color: var(--text-secondary); background: var(--surface-1); border: 1px solid var(--border-default); border-radius: 5px; }
.change-actions .accept { color: var(--text-on-primary); background: var(--color-success); border-color: var(--color-success); }
.change-actions .reject { color: var(--color-danger-text); }
.change-actions button:hover:not(:disabled), .undo:hover:not(:disabled) { border-color: var(--color-primary); }
.change-actions button:disabled, .undo:disabled { opacity: .55; }
.change-compare { min-width: 0; display: grid; grid-template-columns: minmax(0, 1fr) 18px minmax(0, 1fr); align-items: stretch; gap: 7px; }
.change-compare > div { min-width: 0; padding: 9px; background: var(--surface-2); border: 1px solid var(--border-subtle); }
.change-compare > div:last-child { background: var(--surface-2); border-color: var(--border-default); }
.change-compare > svg { align-self: center; color: var(--text-secondary); }
.change-compare span { color: var(--text-secondary); font-size: 11px; }
.change-compare p { margin: 5px 0 0; color: var(--text-primary); font-size: 12px; line-height: 1.65; white-space: pre-wrap; overflow-wrap: anywhere; }
.change-reason { margin: 0; color: var(--text-secondary); font-size: 11.5px; }
.change-item details { color: var(--text-secondary); font-size: 11px; }
.change-item details summary { width: max-content; cursor: pointer; }
.change-item details p { margin: 5px 0 0; display: grid; grid-template-columns: minmax(80px, .35fr) 1fr; gap: 7px; line-height: 1.5; }
.change-item details p span { color: var(--color-primary-text); overflow-wrap: anywhere; }
.change-edit { display: grid; gap: 7px; }
.change-edit textarea { width: 100%; resize: vertical; padding: 9px; color: var(--text-primary); background: var(--surface-1); border: 1px solid var(--color-primary); border-radius: 5px; line-height: 1.6; }
.change-edit > div { display: flex; justify-content: flex-end; gap: 6px; }
.change-edit button { min-height: 29px; padding: 0 10px; border: 1px solid var(--border-default); border-radius: 5px; background: var(--surface-1); }
.change-edit button:last-child { color: var(--text-on-primary); background: var(--color-primary); border-color: var(--color-primary); }
.change-error { margin: 10px 12px 0; padding: 8px; color: var(--color-danger-text); background: var(--surface-2); border-radius: 5px; font-size: 11.5px; }
@media (max-width: 640px) {
  .change-set { width: calc(100% - 36px); margin-left: 36px; }
  .change-compare { grid-template-columns: 1fr; }
  .change-compare > svg { display: none; }
}
</style>

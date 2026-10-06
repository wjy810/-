/** Side drawer tools: language branches, revision history and AI privacy preferences. */
import { ref } from 'vue'
import { errorMessage } from '@/shared/api/types'
import { confirm } from '@/shared/ui/confirm'
import {
  confirmAiResumeLanguageBranch,
  createAiResumeLanguageBranch,
  deleteAiResumeHistory,
  fetchAiResumeBranchDiff,
  fetchAiResumeWritingPreference,
  listAiResumeBranches,
  listAiResumeRevisions,
  restoreAiResumeRevision,
  revokeAiResumeConsent,
  setAiResumeWritingPreference,
  switchAiResumeBranch,
  syncAiResumeBranch,
  translateAiResumeLanguageBranch,
} from '../services/aiResumeApi'
import type { AiResumeBranch, AiResumeBranchDiff, AiResumeRevision, AiWritingStyleCode } from '../types'
import type { WorkbenchTool } from './types'
import type { WorkbenchSession } from './useWorkbenchSession'

export const WRITING_STYLE_OPTIONS: Array<{ value: AiWritingStyleCode; label: string }> = [
  { value: 'SYSTEM_RECOMMENDED', label: '系统推荐' },
  { value: 'PROFESSIONAL_CONCISE', label: '专业简洁' },
  { value: 'RESULTS_ORIENTED', label: '成果导向' },
  { value: 'TECHNICAL_RIGOR', label: '技术严谨' },
  { value: 'STEADY_FORMAL', label: '稳健正式' },
]

export function useWorkbenchTools(session: WorkbenchSession) {
  const active = ref<WorkbenchTool | null>(null)
  /** '' | 'load' | '<action>-<id>' — identifies the button that shows a spinner. */
  const pending = ref('')
  const error = ref('')
  const revisions = ref<AiResumeRevision[]>([])
  const branches = ref<AiResumeBranch[]>([])
  const branchDiff = ref<AiResumeBranchDiff | null>(null)
  const writingStyle = ref<AiWritingStyleCode>('SYSTEM_RECOMMENDED')

  function id(): string | null {
    return session.conversation.value?.id ?? null
  }

  async function run(key: string, fallback: string, task: (conversationId: string) => Promise<void>): Promise<void> {
    const conversationId = id()
    if (!conversationId) return
    pending.value = key
    error.value = ''
    try {
      await task(conversationId)
    } catch (reason) {
      error.value = errorMessage(reason, fallback)
    } finally {
      pending.value = ''
    }
  }

  async function open(tool: WorkbenchTool): Promise<void> {
    active.value = tool
    error.value = ''
    branchDiff.value = null
    await run('load', '工具数据读取失败', async (conversationId) => {
      if (tool === 'history') revisions.value = await listAiResumeRevisions(conversationId)
      if (tool === 'privacy') writingStyle.value = (await fetchAiResumeWritingPreference(conversationId)).code
      if (tool === 'branches') branches.value = await listAiResumeBranches(conversationId)
    })
  }

  function close(): void {
    active.value = null
  }

  async function refreshBranches(conversationId: string): Promise<void> {
    branches.value = await listAiResumeBranches(conversationId)
  }

  const updateWritingStyle = (code: AiWritingStyleCode) => run('privacy-style', '写作风格更新失败', async (conversationId) => {
    writingStyle.value = (await setAiResumeWritingPreference(conversationId, code)).code
    session.notify('写作风格已更新，后续 AI 调用将使用该受控偏好。')
  })

  const revokeConsent = () => run('privacy-consent', 'AI 授权撤销失败', async () => {
    await revokeAiResumeConsent()
    await session.load()
    session.notify('AI 授权已撤销；正式简历内容保持不变。')
  })

  async function deleteHistory(): Promise<void> {
    const accepted = await confirm({
      title: '清除 AI 历史正文？',
      message: '将删除所有消息正文和未决 AI 修改，只保留正式简历和不含正文的最小审计标记。此操作不能恢复。',
      confirmText: '确认清除',
      tone: 'danger',
    })
    if (!accepted) return
    await run('privacy-delete', 'AI 历史清除失败', async (conversationId) => {
      const result = await deleteAiResumeHistory(conversationId)
      await session.load()
      session.notify(`已清除 ${result.messageBodiesDeleted} 条消息正文和 ${result.pendingCandidatesDeleted} 条旧版未决记录；正式简历未删除。`)
    })
  }

  const createLanguageBranch = () => run('branch-create', '语言分支创建失败', async (conversationId) => {
    const current = branches.value.find(branch => branch.active)
    await createAiResumeLanguageBranch(conversationId, current?.languageCode === 'en-US' ? 'zh-CN' : 'en-US')
    await refreshBranches(conversationId)
    session.notify('语言子分支已建立并标记待翻译复核；未把原文伪装成已完成翻译。')
  })

  const activateBranch = (branch: AiResumeBranch) => branch.active ? Promise.resolve() : run(`switch-${branch.id}`, '分支切换失败', async (conversationId) => {
    await switchAiResumeBranch(conversationId, branch.id)
    await Promise.all([session.load(), refreshBranches(conversationId)])
    session.notify(`已切换到“${branch.title}”。`)
  })

  const inspectBranch = (branch: AiResumeBranch) => run(`diff-${branch.id}`, '分支差异读取失败', async (conversationId) => {
    branchDiff.value = await fetchAiResumeBranchDiff(conversationId, branch.id)
  })

  const syncBranch = (branch: AiResumeBranch) => run(`sync-${branch.id}`, '分支同步失败', async (conversationId) => {
    await syncAiResumeBranch(conversationId, branch)
    await Promise.all([session.load(), refreshBranches(conversationId)])
    branchDiff.value = null
    session.notify(`已显式同步“${branch.title}”并生成新修订。`)
  })

  const translateBranch = (branch: AiResumeBranch) => session.aiUsable.value
    ? run(`translate-${branch.id}`, '译文生成失败，语言分支保持不变', async (conversationId) => {
      await translateAiResumeLanguageBranch(conversationId, branch.id)
      await refreshBranches(conversationId)
      session.notify(`“${branch.title}”已生成待确认译文，请核对专有名称和全部事实后确认。`)
    })
    : Promise.resolve()

  const confirmTranslation = (branch: AiResumeBranch) => run(`confirm-translation-${branch.id}`, '译文确认失败', async (conversationId) => {
    await confirmAiResumeLanguageBranch(conversationId, branch)
    await Promise.all([session.load(), refreshBranches(conversationId)])
    session.notify(`“${branch.title}”译文已确认，并生成新的不可变修订。`)
  })

  const restoreRevision = (revision: AiResumeRevision) => run(`restore-${revision.id}`, '版本恢复失败', async (conversationId) => {
    await restoreAiResumeRevision(conversationId, revision.id)
    await Promise.all([session.load(), listAiResumeRevisions(conversationId).then((value) => { revisions.value = value })])
    session.notify(`已把第 ${revision.revisionNo} 版恢复为新的修订，旧历史仍保留。`)
  })

  return {
    active,
    pending,
    error,
    revisions,
    branches,
    branchDiff,
    writingStyle,
    open,
    close,
    updateWritingStyle,
    revokeConsent,
    deleteHistory,
    createLanguageBranch,
    activateBranch,
    inspectBranch,
    syncBranch,
    translateBranch,
    confirmTranslation,
    restoreRevision,
  }
}

export type WorkbenchTools = ReturnType<typeof useWorkbenchTools>

/** User-facing wording of the workbench, kept out of components so it can be reviewed in one place. */
import type { AiIdentity, AiResumeBranch, AiResumeConversation } from '../types'
import { cardMeta } from './cardConfig'
import type { DraftState } from './types'

export function identityIntro(identity: AiIdentity | undefined): { answer: string; reply: string } {
  if (identity === 'STUDENT') return { answer: '我目前是学生', reply: '很好。接下来会优先梳理教育、实践、项目和技能，并把确认内容同步到右侧预览。' }
  if (identity === 'GRADUATE') return { answer: '我目前是应届生', reply: '明白。接下来会围绕校招目标梳理教育、实习、项目和技能，并实时生成第一版。' }
  return { answer: '我目前是职场人士', reply: '明白。接下来会优先梳理目标岗位、工作成果和项目经历，并保持所有事实可确认。' }
}

export function assistantPrompt(cardType: string | undefined, identity: AiIdentity | undefined): string {
  const educationFirst = identity === 'STUDENT' || identity === 'GRADUATE'
  if (cardType === 'TARGET_JOB') return educationFirst
    ? '教育背景已经记录。现在请选择一个标准目标岗位，后续技能和内容建议都会以这个岗位为准。'
    : '我们先从目标开始：请从标准岗位分类中选择你准备投递的岗位。'
  if (cardType === 'EDUCATION') return educationFirst
    ? '接下来先把教育背景梳理清楚。请填写学校、专业、学历和起止时间，确认后会立即同步到右侧预览。'
    : '请告诉我一段教育经历：学校、专业、学历和起止时间。暂时不确定的内容可以稍后补充。'
  if (cardType === 'EXPERIENCE') return '接下来聊一段工作或实习经历。请告诉我公司、岗位、时间，以及你实际做过的事情。'
  if (cardType === 'PROJECTS') return '有能证明能力的项目吗？告诉我项目背景、你的职责和真实结果，我会帮你整理表达。'
  if (cardType === 'SKILLS') return '请告诉我你确实掌握的专业技能。不会的技能不要为了匹配岗位而添加。'
  if (cardType === 'CONTACT') return '简历内容已经有了基础，接下来可以补充用于投递的姓名、邮箱或手机号。'
  if (cardType) return `还可以继续补充${cardMeta(cardType).label}。你可以直接在对话里描述，我会基于真实事实整理。`
  return '你可以继续告诉我教育、目标岗位、经历或想修改的内容，我会一次处理一个问题。'
}

export function aiUnavailableText(conversation: AiResumeConversation | null): string {
  if (!conversation || conversation.aiAvailable) return ''
  return conversation.aiUnavailableReason === 'AI_GATEWAY_DISABLED'
    ? 'AI 通道当前未启用。消息可以保存，但暂时不会生成回复；简历资料、预览和导出仍可使用。'
    : '当前没有通过验收的 AI 通道。消息可以保存，但暂时不会生成回复。'
}

export function draftLabel(state: DraftState | undefined, cardStatus: string): string {
  if (state === 'waiting') return '等待自动保存'
  if (state === 'saving') return '正在保存'
  if (state === 'saved') return '草稿已保存'
  if (state === 'error') return '保存失败'
  if (cardStatus === 'CONFIRMED') return '已确认'
  if (cardStatus === 'SKIPPED') return '已跳过'
  return '尚未确认'
}

export function branchTypeLabel(branch: AiResumeBranch): string {
  return branch.branchType === 'BASE' ? '基础' : branch.branchType === 'JOB' ? '岗位' : '语言'
}

export function languageLabel(code: string): string {
  if (code.startsWith('zh')) return '中文'
  if (code.startsWith('en')) return '英文'
  return '其他语言'
}

export function translationStatus(branch: AiResumeBranch): string {
  const value = branch.reviewMetadata?.translationStatus
  return branch.branchType === 'LANGUAGE' && typeof value === 'string' ? value : ''
}

export function branchStatusLabel(branch: AiResumeBranch, aiAvailable: boolean): string {
  if (branch.syncRequired) return '基础版本有更新，待确认同步'
  const translation = translationStatus(branch)
  if (translation === 'NOT_STARTED') return aiAvailable ? '尚未生成译文' : '尚未生成译文，等待可用 AI 通道'
  if (translation === 'AWAITING_CONFIRMATION') return '译文已生成，待人工确认'
  if (translation === 'CONFIRMED') return '译文已确认'
  return branch.status === 'REVIEWING' ? '待人工复核' : '已同步'
}

export function unconfirmedProperNames(branch: AiResumeBranch): string[] {
  const value = branch.reviewMetadata?.unconfirmedProperNames
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string' && Boolean(item.trim())) : []
}

export function revisionSummary(content: Record<string, unknown>): string {
  const intentions = content.intentions as Record<string, unknown> | undefined
  return String(intentions?.targetJob || content.summary || '尚未填写摘要').slice(0, 72)
}

const REVISION_SOURCES: Record<string, string> = {
  WORKBENCH_CREATED: '创建工作台',
  USER_CONFIRMED: '确认卡片',
  CANDIDATE_CONFIRMED: '采纳候选内容',
  AI_CHANGE_APPLIED: '采纳 AI 修改',
  USER_CORRECTED_AI_CHANGE: '修正后采纳 AI 修改',
  PHOTO_CONFIRMED: '更新照片',
  PHOTO_REMOVED: '隐藏照片',
  VERSION_RESTORED: '恢复历史版本',
  BRANCH_SYNCED: '同步基础版本',
  LANGUAGE_BRANCH_CREATED: '创建语言分支',
  JOB_BRANCH_CREATED: '创建岗位分支',
  LANGUAGE_TRANSLATED: '生成译文',
  LANGUAGE_CONFIRMED: '确认译文',
}

/** Never show a raw enum: unknown sources fall back to a neutral label. */
export function revisionSourceLabel(source: string): string {
  return REVISION_SOURCES[source] ?? '内容更新'
}

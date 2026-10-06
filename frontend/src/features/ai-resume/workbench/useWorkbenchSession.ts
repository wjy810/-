/**
 * Loads one AI resume conversation and keeps it current (docs/03 §4.7 useWorkbenchSession).
 * Other workbench composables subscribe to `onApply` instead of reaching into each other.
 */
import { computed, ref } from 'vue'
import { createEventHook } from '@vueuse/core'
import { errorMessage, isVersionConflict } from '@/shared/api/types'
import { toast } from '@/shared/ui/toast'
import { updateResume } from '@/features/resume/services/resumeApi'
import { normalizeResumeTitle, resumeTitleIssue } from '@/features/resume/utils/resumeTitle'
import {
  fetchAiResume,
  grantAiResumeConsent,
  listAiResumeSmartTemplates,
  removeAiResumePhoto,
  setCareerLibraryEvidencePreference,
  uploadAiResumePhoto,
} from '../services/aiResumeApi'
import type { AiResumeConversation, AiSmartTemplate } from '../types'
import { aiUnavailableText } from './copy'

/** `prompt` brings the latest assistant question into view instead of jumping past it to the bottom. */
export type ChatScrollRequest = { smooth: boolean; force: boolean; anchor: 'bottom' | 'prompt' }

const LIVE_EVENTS = [
  'card.confirmed', 'message.completed', 'message.failed', 'message.cancelled',
  'change-set.created', 'change-item.applied', 'change-item.rejected', 'change-item.stale', 'change-item.undone',
]
const NOTICE_KEY = 'ai-resume-workbench-notice'
const ERROR_KEY = 'ai-resume-workbench-error'

export function useWorkbenchSession(conversationId: () => string) {
  const conversation = ref<AiResumeConversation | null>(null)
  const loading = ref(true)
  const loadError = ref<unknown>(null)
  const templates = ref<AiSmartTemplate[]>([])
  const templatesPending = ref(false)
  /** A chat reply is streaming; structured AI helpers wait for it (and vice versa). */
  const messagePending = ref(false)
  const consentPending = ref(false)
  const careerEvidencePending = ref(false)
  const photoPending = ref(false)
  const renamePending = ref(false)

  let disposed = false
  let eventSource: EventSource | null = null
  let reloadTimer: ReturnType<typeof setTimeout> | undefined
  const applyHooks: Array<(next: AiResumeConversation) => void> = []
  const chatScroll = createEventHook<ChatScrollRequest>()
  /** A card was confirmed: the preview briefly highlights where it landed. */
  const landed = createEventHook<string>()

  const ready = computed(() => conversation.value?.onboardingStage === 'READY_FOR_PREVIEW')
  const aiReason = computed(() => aiUnavailableText(conversation.value))
  const aiUsable = computed(() => Boolean(conversation.value?.aiAvailable) && conversation.value?.consent.status === 'GRANTED')
  const quota = computed(() => conversation.value?.quota ?? null)

  /** A success notice; `action` adds a button (e.g. undo) and keeps the notice up for `timeoutMs`. */
  function notify(message: string, action?: { label: string; run: () => unknown; timeoutMs?: number }): void {
    if (disposed) return
    toast.success(message, {
      dedupeKey: NOTICE_KEY,
      duration: action?.timeoutMs,
      action: action ? { label: action.label, onClick: () => { void action.run() } } : undefined,
    })
  }

  function fail(message: string): void {
    if (!disposed) toast.error(message, { dedupeKey: ERROR_KEY })
  }

  function clearError(): void {
    toast.dismiss(ERROR_KEY)
  }

  function onApply(hook: (next: AiResumeConversation) => void): void {
    applyHooks.push(hook)
  }

  function requestChatScroll(smooth = false, force = true, anchor: ChatScrollRequest['anchor'] = 'bottom'): void {
    void chatScroll.trigger({ smooth, force, anchor })
  }

  /**
   * Accept a whole-conversation response without letting a slower, older response roll back
   * newer cards, layout, design or resume versions that already arrived.
   */
  function applyConversation(next: AiResumeConversation): void {
    if (disposed) return
    const previous = conversation.value
    if (previous?.id === next.id && previous.activeBranchId === next.activeBranchId) {
      next = { ...next, cards: next.cards.map((card) => {
        const known = previous.cards.find(item => item.id === card.id)
        return known && known.versionNo > card.versionNo ? known : card
      }) }
      if (previous.layout && previous.layout.version > (next.layout?.version ?? -1)) {
        next = { ...next, layout: previous.layout, activeDesign: previous.activeDesign, activeTemplate: previous.activeTemplate }
      }
      if (previous.activeDesign?.templateId === next.activeDesign?.templateId
        && (previous.activeDesign?.versionNo ?? -1) > (next.activeDesign?.versionNo ?? -1)) {
        next = { ...next, activeDesign: previous.activeDesign }
      }
      if (previous.resume.version > next.resume.version) next = { ...next, resume: previous.resume, content: previous.content }
    }
    conversation.value = next
    for (const hook of applyHooks) hook(next)
  }

  async function load(showSpinner = false): Promise<void> {
    if (disposed) return
    const id = conversationId()
    if (showSpinner) loading.value = true
    try {
      const next = await fetchAiResume(id)
      if (disposed || conversationId() !== id) return
      applyConversation(next)
      loadError.value = null
    } catch (reason) {
      if (!conversation.value) loadError.value = reason
      else fail(errorMessage(reason, 'AI 工作台读取失败'))
    } finally {
      loading.value = false
    }
  }

  async function loadTemplates(): Promise<void> {
    if (!conversation.value || disposed) return
    const id = conversation.value.id
    templatesPending.value = true
    try {
      const next = await listAiResumeSmartTemplates(id)
      if (!disposed && conversation.value?.id === id) templates.value = next
    } catch (reason) {
      fail(errorMessage(reason, '智能模板读取失败'))
    } finally {
      templatesPending.value = false
    }
  }

  /** Server-sent events only signal "something changed"; a debounced reload fetches the truth. */
  function connectEvents(): void {
    if (!conversation.value || typeof EventSource === 'undefined') return
    eventSource?.close()
    const url = `/api/v1/ai-resume/conversations/${encodeURIComponent(conversation.value.id)}/events?afterSequence=${conversation.value.lastSequence}`
    eventSource = new EventSource(url)
    for (const type of LIVE_EVENTS) {
      eventSource.addEventListener(type, () => {
        clearTimeout(reloadTimer)
        reloadTimer = setTimeout(() => void load(), 180)
      })
    }
  }

  function setRemainingQuota(remaining: number): void {
    const current = conversation.value?.quota
    if (!current) return
    current.remainingUnits = remaining
    current.usedUnits = Math.max(0, current.grantedUnits - remaining - current.heldUnits)
  }

  async function grantConsent(): Promise<void> {
    consentPending.value = true
    try {
      await grantAiResumeConsent()
      await load()
      notify('AI 授权已记录。撤销后会停止新的模型调用。')
    } catch (reason) {
      fail(errorMessage(reason, 'AI 授权失败'))
    } finally {
      consentPending.value = false
    }
  }

  async function toggleCareerEvidence(): Promise<void> {
    if (!conversation.value || careerEvidencePending.value) return
    careerEvidencePending.value = true
    clearError()
    try {
      const preference = await setCareerLibraryEvidencePreference(conversation.value.id, !conversation.value.careerLibraryEvidence.enabled)
      if (conversation.value) conversation.value = { ...conversation.value, careerLibraryEvidence: preference }
      notify(preference.enabled
        ? `已允许本会话引用相关的已确认资料（快照 v${preference.snapshotVersion}）。`
        : '已关闭本会话的求职资料引用。')
    } catch (reason) {
      fail(errorMessage(reason, '求职资料引用设置失败'))
    } finally {
      careerEvidencePending.value = false
    }
  }

  async function uploadPhoto(file: File): Promise<void> {
    if (!conversation.value || photoPending.value) return
    if (!['image/png', 'image/jpeg'].includes(file.type) || file.size > 1024 * 1024) {
      fail('照片仅支持 1MB 以内的 PNG 或 JPEG。')
      return
    }
    photoPending.value = true
    clearError()
    try {
      applyConversation(await uploadAiResumePhoto(conversation.value.id, file))
      notify('照片已保存到私有存储，并生成新的不可变修订。')
    } catch (reason) {
      fail(errorMessage(reason, '照片上传失败，原照片保持不变'))
    } finally {
      photoPending.value = false
    }
  }

  async function removePhoto(): Promise<void> {
    if (!conversation.value || photoPending.value) return
    photoPending.value = true
    clearError()
    try {
      applyConversation(await removeAiResumePhoto(conversation.value.id))
      notify('当前版本已隐藏照片，并生成新的不可变修订。')
    } catch (reason) {
      fail(errorMessage(reason, '照片移除失败'))
    } finally {
      photoPending.value = false
    }
  }

  /** Renames the resume; resolves false (with a visible reason) when nothing was saved. */
  async function renameResume(value: string): Promise<boolean> {
    const current = conversation.value
    if (!current || renamePending.value) return false
    const issue = resumeTitleIssue(value)
    if (issue) {
      fail(issue)
      return false
    }
    const title = normalizeResumeTitle(value)
    if (title === (current.resume.title ?? '').trim()) return true
    renamePending.value = true
    clearError()
    try {
      const resume = await updateResume(current.masterId, { title, expectedVersion: current.resume.version })
      if (conversation.value?.id === current.id) applyConversation({ ...conversation.value, resume: { ...conversation.value.resume, ...resume } })
      notify(`已重命名为「${title}」。`)
      return true
    } catch (reason) {
      if (isVersionConflict(reason)) {
        await load()
        fail('简历刚在其他页面更新过，已重新读取，请再改一次名称。')
      } else {
        fail(errorMessage(reason, '重命名失败，请稍后重试'))
      }
      return false
    } finally {
      renamePending.value = false
    }
  }

  function dispose(): void {
    disposed = true
    eventSource?.close()
    eventSource = null
    clearTimeout(reloadTimer)
  }

  return {
    conversation,
    loading,
    loadError,
    templates,
    templatesPending,
    messagePending,
    consentPending,
    careerEvidencePending,
    photoPending,
    renamePending,
    ready,
    aiReason,
    aiUsable,
    quota,
    isDisposed: () => disposed,
    notify,
    fail,
    clearError,
    onApply,
    onChatScroll: chatScroll.on,
    requestChatScroll,
    onLanded: landed.on,
    markLanded: (cardType: string) => void landed.trigger(cardType),
    applyConversation,
    load,
    loadTemplates,
    connectEvents,
    setRemainingQuota,
    grantConsent,
    toggleCareerEvidence,
    uploadPhoto,
    removePhoto,
    renameResume,
    dispose,
  }
}

export type WorkbenchSession = ReturnType<typeof useWorkbenchSession>

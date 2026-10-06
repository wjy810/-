/** Per-record "AI 帮写" description suggestions; one generation at a time across the workbench. */
import { computed, reactive, ref } from 'vue'
import { errorMessage } from '@/shared/api/types'
import { isAbortError } from '@/shared/lib/pollTask'
import { cancelAiResumeResponse, generateAiResumeDescriptionSuggestion } from '../services/aiResumeApi'
import type { AiResumeCard } from '../types'
import {
  descriptionFactsFingerprint,
  descriptionInputIssue,
  descriptionSuggestionKey,
  mergeDescription,
  type DescriptionSuggestionState,
} from '../utils/descriptionSuggestion'
import { DESCRIPTION_FIELD_LABELS } from './cardConfig'
import type { StructuredItem } from './types'
import type { WorkbenchSession } from './useWorkbenchSession'

export type RecordAccess = {
  records: (card: AiResumeCard) => StructuredItem[]
  updateRecordField: (card: AiResumeCard, index: number, key: string, value: unknown) => void
}

export function useDescriptionAssist(session: WorkbenchSession, access: RecordAccess) {
  const suggestions = reactive<Record<string, DescriptionSuggestionState>>({})
  const controllers = new Map<string, AbortController>()
  const summaryPending = ref(false)

  const descriptionPending = computed(() => Object.values(suggestions).some(state => state.status === 'loading'))
  /** Any structured AI helper is generating; the chat composer waits for it. */
  const fieldGenerationPending = computed(() => descriptionPending.value || summaryPending.value)

  function state(card: AiResumeCard, index: number): DescriptionSuggestionState | undefined {
    return suggestions[descriptionSuggestionKey(card.id, index)]
  }

  function sourceLabels(card: AiResumeCard, index: number): string[] {
    return state(card, index)?.candidate?.sourceFields.map(field => DESCRIPTION_FIELD_LABELS[field] ?? field) ?? []
  }

  function baseDisabledReason(): string {
    const conversation = session.conversation.value
    if (!conversation?.aiAvailable) return 'AI 通道暂不可用，仍可手动填写'
    if (conversation.consent.status !== 'GRANTED') return '请先完成 AI 授权'
    if (session.messagePending.value) return '正在生成对话回复，请稍后再试'
    return ''
  }

  function disabledReason(card: AiResumeCard, index: number): string {
    const base = baseDisabledReason()
    if (base) return base
    if (summaryPending.value) return '正在生成个人简介，请稍后再试'
    if (descriptionPending.value && state(card, index)?.status !== 'loading') return '另一条经历正在生成，请稍后再试'
    return ''
  }

  function summaryDisabledReason(): string {
    return baseDisabledReason() || (descriptionPending.value ? '另一项内容正在生成，请稍后再试' : '')
  }

  function facts(card: AiResumeCard, index: number): Record<string, unknown> {
    return { ...access.records(card)[index] }
  }

  async function generate(card: AiResumeCard, index: number): Promise<void> {
    const conversation = session.conversation.value
    if (!conversation) return
    const blocked = disabledReason(card, index)
    if (blocked) {
      session.fail(blocked)
      return
    }
    const key = descriptionSuggestionKey(card.id, index)
    const requestId = crypto.randomUUID()
    const snapshot = facts(card, index)
    const fingerprint = descriptionFactsFingerprint(snapshot)
    const inputIssue = descriptionInputIssue(card.cardType, snapshot)
    if (inputIssue) {
      suggestions[key] = { status: 'error', requestId, factsFingerprint: fingerprint, error: inputIssue }
      return
    }
    const controller = new AbortController()
    controllers.set(key, controller)
    suggestions[key] = { status: 'loading', requestId, factsFingerprint: fingerprint }
    session.clearError()
    try {
      const candidate = await generateAiResumeDescriptionSuggestion(conversation.id, card.id, index, snapshot, requestId, controller.signal)
      if (suggestions[key]?.requestId !== requestId) return
      if (descriptionFactsFingerprint(facts(card, index)) !== fingerprint) {
        suggestions[key] = { status: 'error', requestId, factsFingerprint: fingerprint, error: '生成期间这条经历已发生变化，请按最新内容重新生成。' }
        return
      }
      suggestions[key] = { status: 'ready', requestId, factsFingerprint: fingerprint, candidate }
      session.setRemainingQuota(candidate.remainingQuota)
    } catch (reason) {
      if (suggestions[key]?.requestId !== requestId || isAbortError(reason)) return
      suggestions[key] = { status: 'error', requestId, factsFingerprint: fingerprint, error: errorMessage(reason, 'AI 帮写失败，补充描述没有变化') }
    } finally {
      if (controllers.get(key) === controller) controllers.delete(key)
    }
  }

  async function cancel(card: AiResumeCard, index: number): Promise<void> {
    const conversation = session.conversation.value
    const key = descriptionSuggestionKey(card.id, index)
    const current = suggestions[key]
    if (!conversation || current?.status !== 'loading') return
    try {
      await cancelAiResumeResponse(conversation.id, current.requestId)
    } catch {
      // The local request is still aborted; the backend discards late output once cancellation is accepted.
    } finally {
      controllers.get(key)?.abort()
      if (suggestions[key]?.requestId === current.requestId) delete suggestions[key]
      session.notify('已取消 AI 帮写，补充描述没有变化。')
    }
  }

  function apply(card: AiResumeCard, index: number, mode: 'append' | 'replace'): void {
    const key = descriptionSuggestionKey(card.id, index)
    const candidate = suggestions[key]?.candidate
    if (!candidate) return
    const current = String(access.records(card)[index]?.description ?? '')
    delete suggestions[key]
    access.updateRecordField(card, index, 'description', mergeDescription(current, candidate.suggestion, mode))
    session.notify(mode === 'append' && current.trim()
      ? 'AI 建议已追加到卡片草稿；确认此模块后才进入正式简历。'
      : 'AI 建议已写入卡片草稿；确认此模块后才进入正式简历。')
  }

  function discard(card: AiResumeCard, index: number): void {
    delete suggestions[descriptionSuggestionKey(card.id, index)]
  }

  /** A field of the record changed: drop a finished suggestion, keep one that is still generating. */
  function invalidate(cardId: string, index: number): void {
    const key = descriptionSuggestionKey(cardId, index)
    if (suggestions[key]?.status !== 'loading') delete suggestions[key]
  }

  /** Records were reordered or removed: cancel and forget every suggestion of the card. */
  function clearCard(cardId: string): void {
    const conversation = session.conversation.value
    for (const key of Object.keys(suggestions).filter(item => item.startsWith(`${cardId}:`))) {
      const controller = controllers.get(key)
      const current = suggestions[key]
      if (current?.status === 'loading' && conversation) {
        void cancelAiResumeResponse(conversation.id, current.requestId).finally(() => controller?.abort())
      } else {
        controller?.abort()
      }
      controllers.delete(key)
      delete suggestions[key]
    }
  }

  function dispose(): void {
    controllers.forEach(controller => controller.abort())
    controllers.clear()
  }

  return {
    suggestions,
    summaryPending,
    descriptionPending,
    fieldGenerationPending,
    state,
    sourceLabels,
    disabledReason,
    summaryDisabledReason,
    generate,
    cancel,
    apply,
    discard,
    invalidate,
    clearCard,
    dispose,
  }
}

export type DescriptionAssist = ReturnType<typeof useDescriptionAssist>

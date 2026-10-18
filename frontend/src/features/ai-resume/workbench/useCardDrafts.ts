/**
 * Per-card drafts with an independent save queue each (docs/03 §4.8):
 * one request in flight per card, local revisions never move backwards, and a response only
 * clears "dirty" when nothing was typed while it was in flight.
 * Unsaved drafts are mirrored locally (draftMirror.ts): they come back after a closed tab, and a
 * version conflict keeps both copies for the user to choose from.
 */
import { computed, nextTick, reactive } from 'vue'
import { errorMessage, isApiClientError } from '@/shared/api/types'
import { saveAiResumeCardDraft, skipAiResumeCard, submitAiResumeCard } from '../services/aiResumeApi'
import type { AiCertificateItemSuggestion, AiResumeCard, AiSkillGroupSuggestion, JobTaxonomySelection } from '../types'
import { credentialDescriptionIssue, mergeCredentialSuggestions } from '../utils/certificateSuggestions'
import { contactSubmitIssue } from '../utils/contactDetails'
import { mergeSkillGroups } from '../utils/skillSuggestions'
import { cardMeta } from './cardConfig'
import {
  conflictMirrorKey,
  draftMirrorKey,
  localDraftStorage,
  readDraftMirror,
  reconcileDraftMirror,
  removeDraftMirror,
  samePayload,
  writeDraftMirror,
  type MirroredDraft,
} from './draftMirror'
import { blankStructuredItem, clonePayload, itemsOf, parseSkillItems, reorder, seedPayloadFromContent } from './structured'
import type { DraftState, StructuredItem } from './types'
import type { WorkbenchSession } from './useWorkbenchSession'

export type CardDraftHooks = {
  /** Records were added, removed or reordered: per-record AI state no longer lines up. */
  onRecordsRestructured: (cardId: string) => void
  /** One record changed: a finished AI suggestion for it may be stale. */
  onRecordEdited: (cardId: string, index: number) => void
}

const DRAFT_DEBOUNCE_MS = 1000
/** Contact details are short to retype and too personal to leave in browser storage. */
const UNMIRRORED = new Set(['CONTACT'])

export function useCardDrafts(session: WorkbenchSession, hooks: CardDraftHooks) {
  const payloads = reactive<Record<string, Record<string, unknown>>>({})
  const draftStates = reactive<Record<string, DraftState>>({})
  const dirty = reactive(new Set<string>())
  const confirming = reactive(new Set<string>())
  const localRevisions: Record<string, number> = {}
  const timers = new Map<string, ReturnType<typeof setTimeout>>()
  const flights = new Map<string, Promise<boolean>>()
  /** Drafts the server refused because the card changed elsewhere, waiting for the user's choice. */
  const conflicts = reactive<Record<string, MirroredDraft>>({})
  const storage = localDraftStorage()
  const reconciledScopes = new Set<string>()

  session.onApply((next) => {
    for (const card of next.cards) {
      // Whole-conversation responses may carry stale payloads for editors the user is typing in.
      if (!dirty.has(card.id) || !payloads[card.id]) payloads[card.id] = seedPayloadFromContent(card.cardType, clonePayload(card.payload), next.content)
      draftStates[card.id] ||= 'idle'
      localRevisions[card.id] ||= 0
    }
    restoreMirrors(next.id, next.activeBranchId, next.cards)
  })

  function mirrorKey(cardId: string): string {
    const conversation = session.conversation.value
    return conversation ? draftMirrorKey(conversation.id, conversation.activeBranchId, cardId) : ''
  }

  function mirrorDraft(card: AiResumeCard): void {
    const key = mirrorKey(card.id)
    if (!key || UNMIRRORED.has(card.cardType)) return
    const baseVersion = findCard(card.id)?.versionNo ?? card.versionNo
    writeDraftMirror(storage, key, { payload: clonePayload(payloads[card.id]), baseVersion, savedAt: new Date().toISOString() })
  }

  /** Once per card and branch: pick up unsent edits, surface conflicts, forget the rest. */
  function restoreMirrors(conversationId: string, branchId: string, cards: AiResumeCard[]): void {
    let resumed = 0
    for (const card of cards) {
      const scope = `${conversationId}:${branchId}:${card.id}`
      if (reconciledScopes.has(scope) || UNMIRRORED.has(card.cardType)) continue
      reconciledScopes.add(scope)
      const key = draftMirrorKey(conversationId, branchId, card.id)
      const refused = readDraftMirror(storage, conflictMirrorKey(key))
      if (refused) {
        if (samePayload(refused.payload, payloads[card.id])) removeDraftMirror(storage, conflictMirrorKey(key))
        else conflicts[card.id] = refused
      }
      const mirror = dirty.has(card.id) ? null : readDraftMirror(storage, key)
      if (!mirror) continue
      const decision = reconcileDraftMirror(mirror, card.versionNo, payloads[card.id] ?? {})
      if (decision === 'resume') {
        payloads[card.id] = clonePayload(mirror.payload)
        dirty.add(card.id)
        localRevisions[card.id] = (localRevisions[card.id] ?? 0) + 1
        draftStates[card.id] = 'waiting'
        scheduleDraft(card.id, 250)
        resumed += 1
      } else {
        if (decision === 'conflict' && !conflicts[card.id]) {
          conflicts[card.id] = mirror
          writeDraftMirror(storage, conflictMirrorKey(key), mirror)
        }
        removeDraftMirror(storage, key)
      }
    }
    if (resumed) session.notify(`已找回 ${resumed} 个模块上次没来得及保存的草稿，正在保存。`)
  }

  /** The user's copy wins: it becomes the draft and is saved against the latest version. */
  function keepLocalDraft(card: AiResumeCard): void {
    const mirror = conflicts[card.id]
    if (!mirror) return
    delete conflicts[card.id]
    removeDraftMirror(storage, conflictMirrorKey(mirrorKey(card.id)))
    hooks.onRecordsRestructured(card.id)
    payloads[card.id] = clonePayload(mirror.payload)
    dirty.add(card.id)
    localRevisions[card.id] = (localRevisions[card.id] ?? 0) + 1
    draftStates[card.id] = 'waiting'
    mirrorDraft(card)
    scheduleDraft(card.id, 0)
  }

  function discardLocalDraft(card: AiResumeCard): void {
    delete conflicts[card.id]
    removeDraftMirror(storage, conflictMirrorKey(mirrorKey(card.id)))
  }

  /** Worst state across all cards, for the header save indicator. */
  const aggregateState = computed<DraftState>(() => {
    const states = Object.values(draftStates)
    if (states.includes('error')) return 'error'
    if (states.includes('saving')) return 'saving'
    if (states.includes('waiting') || dirty.size > 0) return 'waiting'
    return states.includes('saved') ? 'saved' : 'idle'
  })

  function findCard(cardId: string): AiResumeCard | undefined {
    return session.conversation.value?.cards.find(item => item.id === cardId)
  }

  function payloadOf(card: AiResumeCard): Record<string, unknown> {
    return payloads[card.id] ?? {}
  }

  function updateFields(card: AiResumeCard, patch: Record<string, unknown>): void {
    payloads[card.id] ||= {}
    Object.assign(payloads[card.id]!, patch)
    dirty.add(card.id)
    localRevisions[card.id] = (localRevisions[card.id] ?? 0) + 1
    draftStates[card.id] = 'waiting'
    mirrorDraft(card)
    scheduleDraft(card.id)
  }

  function updateField(card: AiResumeCard, key: string, value: unknown): void {
    updateFields(card, { [key]: value })
  }

  function records(card: AiResumeCard): StructuredItem[] {
    return itemsOf(card.cardType, payloads[card.id])
  }

  function updateRecordField(card: AiResumeCard, index: number, key: string, value: unknown): void {
    const items = records(card).map(item => ({ ...item }))
    items[index] = { ...items[index], [key]: value }
    hooks.onRecordEdited(card.id, index)
    updateField(card, 'items', items)
  }

  function updateSkillItems(card: AiResumeCard, index: number, value: string): void {
    updateRecordField(card, index, 'items', parseSkillItems(value))
  }

  function addRecord(card: AiResumeCard): void {
    const existing = Array.isArray(payloads[card.id]?.items) ? records(card) : []
    updateField(card, 'items', [...existing.map(item => ({ ...item })), blankStructuredItem(card.cardType)])
    void nextTick(() => session.requestChatScroll(true))
  }

  function removeRecord(card: AiResumeCard, index: number): void {
    hooks.onRecordsRestructured(card.id)
    const items = records(card).filter((_, itemIndex) => itemIndex !== index)
    updateField(card, 'items', items.length ? items : [blankStructuredItem(card.cardType)])
  }

  function moveRecord(card: AiResumeCard, from: number, to: number): void {
    if (from === to) return
    hooks.onRecordsRestructured(card.id)
    updateField(card, 'items', reorder(records(card).map(item => ({ ...item })), from, to))
  }

  function contactLinks(card: AiResumeCard): string[] {
    const links = payloads[card.id]?.links
    return Array.isArray(links) ? links.map(link => String(link ?? '')) : []
  }

  function addContactLink(card: AiResumeCard): void {
    updateField(card, 'links', [...contactLinks(card), ''])
  }

  function updateContactLink(card: AiResumeCard, index: number, value: string): void {
    const links = [...contactLinks(card)]
    links[index] = value
    updateField(card, 'links', links)
  }

  function removeContactLink(card: AiResumeCard, index: number): void {
    updateField(card, 'links', contactLinks(card).filter((_, itemIndex) => itemIndex !== index))
  }

  function moveContactLink(card: AiResumeCard, from: number, to: number): void {
    updateField(card, 'links', reorder(contactLinks(card), from, to))
  }

  function chooseJob(card: AiResumeCard, selection: JobTaxonomySelection): void {
    updateFields(card, {
      targetJob: selection.job.displayName,
      taxonomyNodeId: selection.job.id,
      taxonomyGroupId: selection.groupId,
      taxonomyCategoryId: selection.categoryId,
      taxonomyCode: selection.job.code,
      catalogOccupationCode: selection.job.catalogOccupationCode,
    })
  }

  function applySkillGroups(card: AiResumeCard, groups: AiSkillGroupSuggestion[]): void {
    updateField(card, 'items', mergeSkillGroups(records(card), groups))
    session.notify('AI 技能建议已合并到草稿；原技能和说明均已保留，确认专业技能模块后才进入正式简历。')
  }

  function applyCredentialItems(card: AiResumeCard, items: AiCertificateItemSuggestion[]): void {
    const kind = card.cardType === 'HONORS' ? 'HONOR' : 'CERTIFICATE'
    updateField(card, 'items', mergeCredentialSuggestions(records(card), items, kind))
    const noun = kind === 'CERTIFICATE' ? '证书' : '荣誉'
    session.notify(`AI ${noun}建议已合并到草稿；同名${noun}保留原内容，只补充缺失字段，新${noun}追加到末尾。`)
  }

  function applySummaryCandidate(card: AiResumeCard, text: string): void {
    updateField(card, 'text', text)
    session.notify('AI 个人简介已写入卡片草稿；确认此模块后才进入正式简历和 PDF。')
  }

  function submitIssue(card: AiResumeCard): string {
    if (card.cardType === 'TARGET_JOB' && !String(payloads[card.id]?.taxonomyNodeId ?? '').trim()) {
      return '请先从标准岗位分类中选择一个岗位。'
    }
    if (card.cardType === 'CERTIFICATES' || card.cardType === 'HONORS') {
      return credentialDescriptionIssue(records(card), card.cardType === 'CERTIFICATES' ? '证书与资质' : '荣誉奖项')
    }
    if (card.cardType === 'LANGUAGES' && !records(card).some(item => String(item.language ?? '').trim())) {
      return '请先选择一种语言，或使用“暂时跳过”。'
    }
    if (card.cardType === 'CONTACT') return contactSubmitIssue(payloads[card.id] ?? {})
    return ''
  }

  function cancelTimer(cardId: string): void {
    const timer = timers.get(cardId)
    if (timer) clearTimeout(timer)
    timers.delete(cardId)
  }

  function scheduleDraft(cardId: string, delay = DRAFT_DEBOUNCE_MS): void {
    cancelTimer(cardId)
    timers.set(cardId, setTimeout(() => void saveDraft(cardId), delay))
  }

  function saveDraft(cardId: string): Promise<boolean> {
    cancelTimer(cardId)
    const pending = flights.get(cardId)
    if (pending) return pending
    if (confirming.has(cardId)) {
      scheduleDraft(cardId, 250)
      return Promise.resolve(false)
    }
    const flight = drain(cardId).finally(() => { flights.delete(cardId) })
    flights.set(cardId, flight)
    return flight
  }

  async function drain(cardId: string): Promise<boolean> {
    while (dirty.has(cardId)) {
      const card = findCard(cardId)
      const conversation = session.conversation.value
      if (session.isDisposed() || !card || !conversation) return false
      const conversationId = conversation.id
      const branchId = conversation.activeBranchId
      draftStates[cardId] = 'saving'
      const capturedRevision = localRevisions[cardId]
      const capturedPayload = clonePayload(payloads[cardId])
      try {
        const next = await saveAiResumeCardDraft(conversationId, cardId, capturedPayload, card.versionNo)
        if (!isSameScope(conversationId, branchId)) return false
        if (localRevisions[cardId] === capturedRevision) dirty.delete(cardId)
        session.applyConversation(next)
        draftStates[cardId] = dirty.has(cardId) ? 'waiting' : 'saved'
        settleMirror(cardId)
      } catch (reason) {
        if (!isSameScope(conversationId, branchId)) return false
        if (isApiClientError(reason) && reason.category === 'CONFLICT') {
          await keepRefusedDraft(cardId)
          return false
        }
        draftStates[cardId] = 'error'
        session.fail(errorMessage(reason, '卡片草稿保存失败'))
        return false
      }
    }
    return !session.isDisposed()
  }

  /** A saved draft needs no mirror; one still being typed is re-based on the card's new version. */
  function settleMirror(cardId: string): void {
    const card = findCard(cardId)
    if (!card) return
    if (dirty.has(cardId)) mirrorDraft(card)
    else removeDraftMirror(storage, mirrorKey(cardId))
  }

  /**
   * The card changed elsewhere (another tab or device). Show the latest copy and keep the refused one
   * aside, instead of re-sending it over the other change on the next keystroke.
   */
  async function keepRefusedDraft(cardId: string): Promise<void> {
    const mine = clonePayload(payloads[cardId])
    const key = mirrorKey(cardId)
    dirty.delete(cardId)
    removeDraftMirror(storage, key)
    await session.load()
    const fresh = findCard(cardId)
    draftStates[cardId] = 'idle'
    if (!fresh || samePayload(mine, payloads[cardId])) return
    const refused = { payload: mine, baseVersion: fresh.versionNo, savedAt: new Date().toISOString() }
    conflicts[cardId] = refused
    if (!UNMIRRORED.has(fresh.cardType)) writeDraftMirror(storage, conflictMirrorKey(key), refused)
    session.warn(`“${cardMeta(fresh.cardType).label}”在别处更新过，已显示最新内容；你的修改另存在卡片上，可以选择改用。`)
  }

  function isSameScope(conversationId: string, branchId: string): boolean {
    const current = session.conversation.value
    return !session.isDisposed() && current?.id === conversationId && current.activeBranchId === branchId
  }

  /** Saves any pending draft of the card first, then runs the confirm / skip request. */
  async function settle(card: AiResumeCard, run: (fresh: AiResumeCard, conversationId: string) => ReturnType<typeof submitAiResumeCard>,
    fallback: string, onBeforeRun?: () => void): Promise<boolean> {
    if (session.isDisposed() || confirming.has(card.id)) return false
    session.clearError()
    cancelTimer(card.id)
    if ((dirty.has(card.id) || flights.has(card.id)) && !(await saveDraft(card.id))) return false
    const fresh = findCard(card.id)
    const conversation = session.conversation.value
    if (!fresh || !conversation || session.isDisposed()) return false
    const conversationId = conversation.id
    const branchId = conversation.activeBranchId
    const capturedRevision = localRevisions[card.id]
    confirming.add(card.id)
    onBeforeRun?.()
    draftStates[card.id] = 'saving'
    try {
      const next = await run(fresh, conversationId)
      if (!isSameScope(conversationId, branchId)) return false
      if (localRevisions[card.id] === capturedRevision) dirty.delete(card.id)
      session.applyConversation(next)
      draftStates[card.id] = dirty.has(card.id) ? 'waiting' : 'saved'
      settleMirror(card.id)
      // Edits typed while the confirmation was in flight stay as a new draft.
      if (dirty.has(card.id)) scheduleDraft(card.id, 250)
      return true
    } catch (reason) {
      draftStates[card.id] = 'error'
      session.fail(errorMessage(reason, fallback))
      return false
    } finally {
      confirming.delete(card.id)
    }
  }

  async function submit(card: AiResumeCard): Promise<boolean> {
    const ok = await settle(
      card,
      (fresh, conversationId) => submitAiResumeCard(conversationId, card.id, clonePayload(payloads[card.id]), fresh.versionNo),
      '卡片提交失败，正式简历没有变化',
    )
    if (ok) {
      session.notify(`“${cardMeta(card.cardType).label}”已确认，并生成新的不可变修订。`)
      session.markLanded(card.cardType)
    }
    return ok
  }

  async function skip(card: AiResumeCard): Promise<boolean> {
    if (card.cardType !== 'LANGUAGES' || !session.conversation.value) return false
    const ok = await settle(
      card,
      (fresh, conversationId) => skipAiResumeCard(conversationId, card.id, fresh.versionNo),
      '语言能力跳过失败',
      () => hooks.onRecordsRestructured(card.id),
    )
    if (ok) session.notify('已跳过语言能力；之后仍可在“编辑”视图中补充。')
    return ok
  }

  /** Saves every dirty card now (export, leaving the page, mod+S). */
  async function flushAll(): Promise<boolean> {
    const ids = new Set([...dirty, ...flights.keys()])
    const results = await Promise.all([...ids].map(id => saveDraft(id)))
    return results.every(Boolean)
  }

  function dispose(): void {
    timers.forEach(timer => clearTimeout(timer))
    timers.clear()
  }

  return {
    payloads,
    draftStates,
    dirty,
    confirming,
    conflicts,
    keepLocalDraft,
    discardLocalDraft,
    aggregateState,
    payloadOf,
    updateField,
    updateFields,
    records,
    updateRecordField,
    updateSkillItems,
    addRecord,
    removeRecord,
    moveRecord,
    contactLinks,
    addContactLink,
    updateContactLink,
    removeContactLink,
    moveContactLink,
    chooseJob,
    applySkillGroups,
    applyCredentialItems,
    applySummaryCandidate,
    submitIssue,
    saveDraft,
    submit,
    skip,
    flushAll,
    dispose,
  }
}

export type CardDrafts = ReturnType<typeof useCardDrafts>

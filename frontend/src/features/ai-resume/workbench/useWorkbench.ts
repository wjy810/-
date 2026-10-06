/**
 * Composition root of the AI resume workbench. The page creates it once and provides it;
 * every workbench component reads it with `useWorkbench()` (docs/03 §4.7).
 */
import { computed, inject, nextTick, onBeforeUnmount, onMounted, provide, ref, watch, type InjectionKey } from 'vue'
import { useEventListener, useMediaQuery } from '@vueuse/core'
import { isEditableTarget, matchesShortcut } from '@/shared/lib/keyboard'
import type { AiResumeCard } from '../types'
import { cardMeta, SECTION_CARD } from './cardConfig'
import { buildPreviewContent, buildPreviewResume } from './preview'
import { objectRecord } from './structured'
import { WORKBENCH_VIEWS, type DraftState, type MobilePane, type WorkbenchView } from './types'
import { useCardDrafts } from './useCardDrafts'
import { useConversation } from './useConversation'
import { useDescriptionAssist } from './useDescriptionAssist'
import { useDesignDraft } from './useDesignDraft'
import { useGuidedFlow } from './useGuidedFlow'
import { usePdfExport } from './usePdfExport'
import { useWorkbenchSession } from './useWorkbenchSession'
import { useWorkbenchTools } from './useWorkbenchTools'

export const MOBILE_QUERY = '(max-width: 899px)'

export function createWorkbench(conversationId: () => string, options: { source?: () => unknown } = {}) {
  const session = useWorkbenchSession(conversationId)
  // Description assist and drafts reference each other only through these late-bound callbacks.
  const assist = useDescriptionAssist(session, {
    records: card => drafts.records(card),
    updateRecordField: (card, index, key, value) => drafts.updateRecordField(card, index, key, value),
  })
  const drafts = useCardDrafts(session, {
    onRecordsRestructured: cardId => assist.clearCard(cardId),
    onRecordEdited: (cardId, index) => assist.invalidate(cardId, index),
  })
  const guided = useGuidedFlow(session)
  const chat = useConversation(session, assist)
  const pdf = usePdfExport(session, {
    design: () => design,
    previewBasics: () => objectRecord(previewContent.value.basics),
    unconfirmedDraftCount: () => drafts.dirty.size,
  })
  const design = useDesignDraft(session, { isExporting: () => pdf.pending.value })
  const tools = useWorkbenchTools(session)

  const view = ref<WorkbenchView>('conversation')
  const mobilePane = ref<MobilePane>('workspace')
  const moduleListOpen = ref(true)
  /** Set when a module is opened from outside the editor; the editor consumes it (possibly after mounting). */
  const focusPending = ref(false)
  const isMobile = useMediaQuery(MOBILE_QUERY)

  const isDirty = (cardId: string) => drafts.dirty.has(cardId)
  const previewResume = computed(() => session.conversation.value ? buildPreviewResume(session.conversation.value, drafts.payloads, isDirty) : null)
  const previewContent = computed<Record<string, unknown>>(() => session.conversation.value
    ? buildPreviewContent(session.conversation.value, drafts.payloads, isDirty) : {})

  /** One indicator for card drafts and design settings in the header. */
  const saveState = computed<DraftState>(() => {
    const states = [drafts.aggregateState.value, design.saveState.value]
    for (const state of ['error', 'saving', 'waiting', 'saved'] as const) if (states.includes(state)) return state
    return 'idle'
  })

  function openView(next: WorkbenchView): void {
    view.value = next
    mobilePane.value = 'workspace'
    if (next === 'edit') moduleListOpen.value = true
  }

  function openCard(card: AiResumeCard): void {
    guided.select(card)
    openView('edit')
    focusPending.value = true
  }

  /** Click on a preview section → open that module in the editor. */
  function locateSection(section: string): void {
    const type = section === 'header' ? 'CONTACT' : SECTION_CARD[section]
    const card = guided.editableCards.value.find(item => item.cardType === type)
    if (card) openCard(card)
  }

  async function confirmCard(card: AiResumeCard): Promise<void> {
    if (!(await drafts.submit(card))) return
    guided.afterConfirm(card)
    await nextTick()
    session.requestChatScroll(true, true, 'prompt')
  }

  async function skipCard(card: AiResumeCard): Promise<void> {
    if (!(await drafts.skip(card))) return
    guided.afterSkip()
    await nextTick()
    session.requestChatScroll(true, true, 'prompt')
  }

  function addAnotherRecord(): void {
    const card = guided.addAnother()
    if (card) drafts.addRecord(card)
  }

  function continueAfterRepeat(): void {
    guided.continueAfterRepeat()
    void nextTick(() => session.requestChatScroll(true, true, 'prompt'))
  }

  /** mod+S: save everything now and say so. */
  async function saveNow(): Promise<void> {
    const [cardsSaved, designSaved] = await Promise.all([drafts.flushAll(), design.persist()])
    if (cardsSaved && designSaved) session.notify('草稿与设计已全部保存。')
  }

  function hasUnsavedWork(): boolean {
    return drafts.dirty.size > 0 || design.isDirty()
  }

  function onKeydown(event: KeyboardEvent): void {
    if (event.defaultPrevented || event.isComposing || !session.conversation.value) return
    if (matchesShortcut(event, 'mod+s')) {
      event.preventDefault()
      void saveNow()
      return
    }
    if (matchesShortcut(event, 'mod+e')) {
      event.preventDefault()
      pdf.show()
      return
    }
    for (const [index, target] of WORKBENCH_VIEWS.entries()) {
      if (matchesShortcut(event, `alt+${index + 1}`)) {
        event.preventDefault()
        openView(target)
        return
      }
    }
    const bareSlash = event.key === '/' && !isEditableTarget(event.target) && !event.metaKey && !event.ctrlKey && !event.altKey
    if (matchesShortcut(event, 'mod+/') || bareSlash) {
      event.preventDefault()
      openView('conversation')
      void nextTick(() => document.querySelector<HTMLTextAreaElement>('[data-workbench-composer]')?.focus())
    }
  }

  useEventListener(typeof window === 'undefined' ? undefined : window, 'keydown', onKeydown)
  useEventListener(typeof window === 'undefined' ? undefined : window, 'beforeunload', (event: BeforeUnloadEvent) => {
    if (!hasUnsavedWork()) return
    void drafts.flushAll()
    void design.persist()
    event.preventDefault()
  })

  watch(isMobile, (mobile) => {
    if (!mobile) mobilePane.value = 'workspace'
  })

  onMounted(async () => {
    await session.load(true)
    if (session.isDisposed() || !session.conversation.value) return
    await session.loadTemplates()
    if (session.isDisposed()) return
    if (options.source?.() === 'import') {
      session.notify('简历已导入。请在“编辑”中逐个模块核对，时间、职位等细节可能需要补全。')
    }
    session.connectEvents()
    await nextTick()
    session.requestChatScroll(false, true, 'prompt')
  })

  onBeforeUnmount(() => {
    session.dispose()
    chat.dispose()
    assist.dispose()
    pdf.dispose()
    drafts.dispose()
    design.dispose()
  })

  return {
    session,
    drafts,
    assist,
    guided,
    chat,
    design,
    pdf,
    tools,
    view,
    mobilePane,
    moduleListOpen,
    focusPending,
    isMobile,
    previewResume,
    previewContent,
    saveState,
    cardMeta,
    openView,
    openCard,
    locateSection,
    confirmCard,
    skipCard,
    addAnotherRecord,
    continueAfterRepeat,
    saveNow,
    hasUnsavedWork,
  }
}

export type Workbench = ReturnType<typeof createWorkbench>

const WORKBENCH_KEY: InjectionKey<Workbench> = Symbol('ai-resume-workbench')

export function provideWorkbench(workbench: Workbench): void {
  provide(WORKBENCH_KEY, workbench)
}

export function useWorkbench(): Workbench {
  const workbench = inject(WORKBENCH_KEY, null)
  if (!workbench) throw new Error('useWorkbench() must be used inside the AI resume workbench page.')
  return workbench
}

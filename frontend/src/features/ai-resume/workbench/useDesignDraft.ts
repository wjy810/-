/**
 * Template choice and per-template design settings (resume-design-v2, docs/phase2/03 §6.2). Edits are
 * saved serially through `createDraftSaveQueue`, mirrored to localStorage, and flushed before
 * switching templates or exporting. The preview reports its measured layout back here, so page
 * overflow and one-click compaction use the same numbers the PDF renderer will produce.
 */
import { computed, ref, shallowRef, watch } from 'vue'
import { errorMessage } from '@/shared/api/types'
import { createDraftSaveQueue } from '@/shared/lib/draftSaveQueue'
import { fetchCurrentResumeLayout } from '@/features/resume/services/resumeApi'
import type { LayoutResult } from '@/resume-render/components/ResumeDocument.vue'
import { SECTION_TITLES } from '@/resume-render/model/labels'
import type { SectionKey } from '@/resume-render/model/types'
import { compactSteps, type ResumeDesignV2 } from '@/resume-render/theme/design'
import { coerceDesign, defaultDesign } from '@/resume-render/theme/tokens'
import type { TemplateManifest, TemplateModule } from '@/resume-render/templates/manifest'
import { manifestOf } from '@/resume-render/templates/manifests'
import { loadTemplate } from '@/resume-render/templates/registry'
import { regionFor } from '@/resume-render/layout/blocks'
import { saveAiResumeDesign, selectAiResumeTemplate } from '../services/aiResumeApi'
import { reorder } from './structured'
import type { DraftState } from './types'
import type { WorkbenchSession } from './useWorkbenchSession'

const DESIGN_DEBOUNCE_MS = 500
const VARIANT = 'DEFAULT'
export const SECTION_TITLE_MAX = 16

function clone<T>(value: T): T {
  return JSON.parse(JSON.stringify(value)) as T
}

function parseManifest(json: string | null | undefined): TemplateManifest | null {
  if (!json) return null
  try {
    const value = JSON.parse(json) as TemplateManifest
    return value && Array.isArray(value.palettes) && Array.isArray(value.regions) ? value : null
  } catch {
    return null
  }
}

export interface DesignSection {
  key: SectionKey
  title: string
  defaultTitle: string
  region: string
  visible: boolean
}

export function useDesignDraft(session: WorkbenchSession, options: { isExporting: () => boolean }) {
  const draft = ref<ResumeDesignV2 | null>(null)
  const version = ref(0)
  const saveState = ref<DraftState>('idle')
  const templatePending = ref(false)
  const switchingTemplateId = ref('')
  /** Measured by the preview (same engine as the PDF). */
  const layoutResult = shallowRef<LayoutResult | null>(null)
  /** Design before "one-click compact", so the user can undo it. */
  const compactUndo = shallowRef<ResumeDesignV2 | null>(null)
  const compacting = ref(false)
  const templateModule = shallowRef<TemplateModule | null>(null)
  const templateLoadError = ref('')
  let identity = ''

  const conversation = session.conversation

  const activeTemplate = computed(() => session.templates.value.find(item => item.templateId === conversation.value?.layout?.templateId)
    ?? conversation.value?.activeTemplate ?? session.templates.value[0] ?? null)
  /** This build's manifest wins (it matches the layout code); the server copy covers ids it does not know. */
  const manifest = computed<TemplateManifest | null>(() => {
    const id = activeTemplate.value?.templateId ?? conversation.value?.layout?.templateId
    return (id ? manifestOf(id) : undefined)
      ?? parseManifest(activeTemplate.value?.layoutDefinitionJson ?? conversation.value?.layout?.layoutDefinitionJson) ?? null
  })
  const activeDesign = computed<ResumeDesignV2 | null>(() => {
    if (!manifest.value) return null
    const stored = draft.value ?? conversation.value?.activeDesign?.settings ?? activeTemplate.value?.design.settings ?? null
    return coerceDesign(manifest.value, stored)
  })
  const pageLimit = computed(() => layoutResult.value?.pageLimit ?? manifest.value?.maxPages ?? 1)
  const overflow = computed(() => {
    const result = layoutResult.value
    if (!result || result.pageCount <= result.pageLimit) return null
    const key = result.firstOverflowSection as SectionKey | null
    return {
      pageCount: result.pageCount,
      pageLimit: result.pageLimit,
      overflowMm: result.overflowMm,
      section: key ? sectionTitle(key) : null,
    }
  })

  watch(() => manifest.value?.id, async (id) => {
    templateLoadError.value = ''
    layoutResult.value = null
    if (!id) {
      templateModule.value = null
      return
    }
    try {
      const loaded = await loadTemplate(id)
      if (manifest.value?.id === id) templateModule.value = loaded
    } catch {
      if (manifest.value?.id === id) {
        templateModule.value = null
        templateLoadError.value = '模板样式加载失败，请刷新页面重试。'
      }
    }
  }, { immediate: true })

  function sectionTitle(key: SectionKey): string {
    const custom = activeDesign.value?.sectionTitles[key]?.trim()
    return custom || defaultSectionTitle(key)
  }

  function defaultSectionTitle(key: SectionKey): string {
    const value = manifest.value
    if (!value) return SECTION_TITLES['zh-CN'][key]
    return value.sectionTitles?.[key] ?? SECTION_TITLES[value.locale][key]
  }

  /** Sections in render order with title, column and visibility. */
  const orderedSections = computed<DesignSection[]>(() => {
    const value = manifest.value
    const current = activeDesign.value
    if (!value || !current) return []
    const hidden = new Set(current.hiddenSections)
    return current.sectionOrder.map(key => ({
      key,
      title: sectionTitle(key),
      defaultTitle: defaultSectionTitle(key),
      region: regionFor(key, value, current),
      visible: !hidden.has(key),
    }))
  })

  const saver = createDraftSaveQueue({
    identity: () => identity,
    snapshot: () => {
      if (!conversation.value || !activeTemplate.value || !draft.value) return null
      return {
        conversationId: conversation.value.id,
        masterId: conversation.value.masterId,
        templateId: activeTemplate.value.templateId,
        settings: clone(draft.value),
        version: version.value,
      }
    },
    recovery: {
      key: () => `jobproof:ai-resume:design:${identity}`,
      serialize: snapshot => JSON.stringify({ settings: snapshot.settings }),
    },
    save: snapshot => saveAiResumeDesign(snapshot.conversationId, snapshot.templateId, VARIANT, snapshot.settings, snapshot.version),
    accept: async (saved, snapshot, context) => {
      if (!conversation.value) return
      version.value = saved.versionNo
      conversation.value.activeDesign = saved
      if (context.isLatest() && manifest.value) draft.value = coerceDesign(manifest.value, saved.settings)
      const index = session.templates.value.findIndex(item => item.templateId === saved.templateId)
      if (index >= 0) session.templates.value[index] = { ...session.templates.value[index]!, design: saved }
      try {
        const current = await fetchCurrentResumeLayout(snapshot.masterId)
        if (context.isCurrent() && conversation.value) conversation.value = { ...conversation.value, layout: current.layout ?? null }
      } catch {
        if (context.isCurrent()) session.fail('设计已保存，但版式状态暂未刷新；重新打开工作台后会自动恢复。')
      }
    },
    onState: (state) => { saveState.value = state },
    onError: reason => session.fail(errorMessage(reason, '设计保存失败，当前修改仍保留，请重试后再导出')),
    onRecoveryError: (operation) => {
      if (operation === 'read') session.fail('无法读取本地设计副本，请确认浏览器允许本地存储。')
      if (operation === 'write') session.fail('浏览器无法保留本地设计副本，请在离开前确认服务端保存成功。')
    },
  })

  session.onApply((next) => {
    const nextIdentity = `${next.id}:${next.layout?.templateId ?? next.activeDesign?.templateId ?? ''}`
    // Keep unsaved edits of the same template; re-seed when the template changes or nothing is pending.
    if (identity === nextIdentity && (saver.dirty || saver.saving)) return
    if (identity !== nextIdentity) compactUndo.value = null
    identity = nextIdentity
    saver.reset()
    const settings = next.activeDesign?.settings ?? next.layout?.design ?? null
    draft.value = settings && manifest.value ? coerceDesign(manifest.value, settings) : null
    version.value = next.activeDesign?.versionNo ?? 0
    saveState.value = 'idle'
    try {
      const local = saver.readRecovery()
      if (local && settings && manifest.value) {
        const backup = JSON.parse(local) as { settings?: Partial<ResumeDesignV2> }
        if (backup.settings?.schemaVersion === 'resume-design-v2') {
          draft.value = coerceDesign(manifest.value, { ...draft.value, ...backup.settings })
          saver.reset(true)
          saveState.value = 'waiting'
        }
      }
    } catch {
      session.fail('无法读取本地设计副本，请确认浏览器允许本地存储。')
    }
  })

  function commit(next: ResumeDesignV2): void {
    if (templatePending.value || session.isDisposed() || !manifest.value) return
    draft.value = coerceDesign(manifest.value, next)
    saver.changed(DESIGN_DEBOUNCE_MS)
  }

  function update<K extends keyof ResumeDesignV2>(key: K, value: ResumeDesignV2[K]): void {
    if (!activeDesign.value) return
    commit({ ...clone(activeDesign.value), [key]: value })
  }

  function updatePhoto(patch: Partial<ResumeDesignV2['photo']>): void {
    if (!activeDesign.value) return
    update('photo', { ...activeDesign.value.photo, ...patch })
  }

  function toggleSection(key: SectionKey, visible: boolean): void {
    const hidden = new Set(activeDesign.value?.hiddenSections ?? [])
    if (visible) hidden.delete(key)
    else hidden.add(key)
    update('hiddenSections', Array.from(hidden))
  }

  function moveSection(from: number, to: number): void {
    const order = orderedSections.value.map(section => section.key)
    if (to < 0 || to >= order.length || from === to) return
    update('sectionOrder', reorder(order, from, to))
  }

  function renameSection(key: SectionKey, title: string): void {
    if (!activeDesign.value) return
    // eslint-disable-next-line no-control-regex
    const clean = title.replace(/[\u0000-\u001f]/g, '').trim().slice(0, SECTION_TITLE_MAX)
    const titles = { ...activeDesign.value.sectionTitles }
    if (!clean || clean === defaultSectionTitle(key)) delete titles[key]
    else titles[key] = clean
    update('sectionTitles', titles)
  }

  function assignRegion(key: SectionKey, region: string): void {
    const value = manifest.value
    if (!activeDesign.value || !value) return
    const assignments = { ...activeDesign.value.regionAssignments }
    const home = value.regions.find(item => item.sections.includes(key))?.id
    if (region === home) delete assignments[key]
    else assignments[key] = region
    update('regionAssignments', assignments)
  }

  function resetToDefaults(): void {
    if (!manifest.value) return
    compactUndo.value = null
    commit(defaultDesign(manifest.value))
  }

  /** Called by the preview after every re-measure. */
  let layoutWaiter: ((result: LayoutResult) => void) | null = null
  function reportLayout(result: LayoutResult): void {
    layoutResult.value = result
    layoutWaiter?.(result)
    layoutWaiter = null
  }

  function nextLayout(): Promise<LayoutResult | null> {
    return new Promise((resolve) => {
      const timer = setTimeout(() => { layoutWaiter = null; resolve(null) }, 3000)
      layoutWaiter = (result) => { clearTimeout(timer); resolve(result) }
    })
  }

  /**
   * One-click compact (RND-03): tighten spacing, line height, font size, then margins, one step at a
   * time, stopping as soon as the measured layout fits. The previous design can be restored.
   */
  async function compact(): Promise<'fit' | 'partial' | 'none'> {
    const start = activeDesign.value
    if (!start || compacting.value || !overflow.value) return 'none'
    const steps = compactSteps(start)
    if (!steps.length) return 'none'
    compacting.value = true
    compactUndo.value = clone(start)
    try {
      for (const step of steps) {
        const measured = nextLayout()
        commit(step)
        const result = await measured
        if (result && result.pageCount <= result.pageLimit) return 'fit'
      }
      return 'partial'
    } finally {
      compacting.value = false
    }
  }

  function undoCompact(): void {
    if (!compactUndo.value) return
    const previous = compactUndo.value
    compactUndo.value = null
    commit(previous)
  }

  function persist(): Promise<boolean> {
    return saver.flush()
  }

  async function changeTemplate(templateId: string): Promise<void> {
    const current = conversation.value
    if (!current || templatePending.value || options.isExporting() || session.isDisposed()) return
    if (templateId === current.layout?.templateId) return
    templatePending.value = true
    switchingTemplateId.value = templateId
    session.clearError()
    const conversationId = current.id
    const previousTemplateId = current.layout?.templateId
    try {
      if (!(await persist()) || session.isDisposed() || conversation.value?.id !== conversationId) return
      const next = await selectAiResumeTemplate(conversationId, templateId, conversation.value.layout?.version)
      if (session.isDisposed() || conversation.value?.id !== conversationId) return
      session.applyConversation(next)
      await session.loadTemplates()
      session.notify('模板已切换，内容与字号、间距等通用设置已保留。', previousTemplateId
        ? { label: '撤销', run: () => changeTemplate(previousTemplateId), timeoutMs: 10_000 }
        : undefined)
    } catch (reason) {
      session.fail(errorMessage(reason, '模板切换失败，原版式保持不变'))
      await session.load()
    } finally {
      templatePending.value = false
      switchingTemplateId.value = ''
    }
  }

  return {
    draft,
    saveState,
    templatePending,
    switchingTemplateId,
    activeTemplate,
    manifest,
    templateModule,
    templateLoadError,
    activeDesign,
    layoutResult,
    pageLimit,
    overflow,
    compacting,
    compactUndo,
    orderedSections,
    isDirty: () => saver.dirty,
    sectionTitle,
    update,
    updatePhoto,
    toggleSection,
    moveSection,
    renameSection,
    assignRegion,
    resetToDefaults,
    reportLayout,
    compact,
    undoCompact,
    persist,
    changeTemplate,
    dispose: () => saver.dispose(),
  }
}

export type DesignDraft = ReturnType<typeof useDesignDraft>

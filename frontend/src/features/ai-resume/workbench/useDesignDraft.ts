/**
 * Template choice and per-template design settings. Edits are saved serially through
 * `createDraftSaveQueue`, mirrored to localStorage, and flushed before switching templates or exporting.
 */
import { computed, ref } from 'vue'
import { errorMessage } from '@/shared/api/types'
import { createDraftSaveQueue } from '@/shared/lib/draftSaveQueue'
import { fetchCurrentResumeLayout } from '@/features/resume/services/resumeApi'
import type { ResumeDesignSettings } from '@/features/resume/types'
import { saveAiResumeDesign, selectAiResumeTemplate } from '../services/aiResumeApi'
import { SECTION_LABELS } from './cardConfig'
import { reorder } from './structured'
import type { DraftState } from './types'
import type { WorkbenchSession } from './useWorkbenchSession'

function cloneDesign(value: ResumeDesignSettings): ResumeDesignSettings {
  return JSON.parse(JSON.stringify(value)) as ResumeDesignSettings
}

const DESIGN_DEBOUNCE_MS = 500

export function useDesignDraft(session: WorkbenchSession, options: { isExporting: () => boolean }) {
  const draft = ref<ResumeDesignSettings | null>(null)
  const variant = ref('')
  const version = ref(0)
  const saveState = ref<DraftState>('idle')
  const templatePending = ref(false)
  const switchingTemplateId = ref('')
  let identity = ''

  const conversation = session.conversation

  const activeTemplate = computed(() => session.templates.value.find(item => item.templateId === conversation.value?.layout?.templateId)
    ?? conversation.value?.activeTemplate ?? session.templates.value[0] ?? null)
  const activeDesign = computed(() => draft.value ?? conversation.value?.activeDesign?.settings ?? activeTemplate.value?.design.settings ?? null)
  const layoutJson = computed(() => activeTemplate.value?.layoutDefinitionJson ?? conversation.value?.layout?.layoutDefinitionJson ?? null)
  const rendererProtocol = computed(() => activeTemplate.value?.rendererProtocol ?? conversation.value?.layout?.rendererProtocol ?? 'resume-layout-v3')
  const activeVariant = computed(() => variant.value || conversation.value?.activeDesign?.variantCode
    || activeTemplate.value?.design.variantCode || conversation.value?.layout?.variantCode || 'MONO')
  const layoutDefinition = computed<Record<string, unknown>>(() => {
    try { return JSON.parse(layoutJson.value || '{}') as Record<string, unknown> } catch { return {} }
  })
  const accentColors = computed(() => {
    const tokens = layoutDefinition.value.tokens
    const entries = tokens && typeof tokens === 'object' ? Object.entries(tokens as Record<string, unknown>) : []
    return Array.from(new Set(entries
      .filter(([key, value]) => key.startsWith('accent.') && typeof value === 'string')
      .map(([, value]) => String(value).toUpperCase())))
  })
  const sections = computed(() => {
    const slots = Array.isArray(layoutDefinition.value.slots) ? layoutDefinition.value.slots as Array<Record<string, unknown>> : []
    return slots
      .map(slot => ({ key: String(slot.key ?? ''), label: String(slot.label ?? SECTION_LABELS[String(slot.key ?? '')] ?? slot.key ?? '') }))
      .filter(slot => slot.key)
  })
  /** Sections in the order the template will render them, with visibility. */
  const orderedSections = computed(() => {
    const known = new Map(sections.value.map(section => [section.key, section]))
    const order = (activeDesign.value?.sectionOrder ?? []).filter(key => known.has(key))
    const rest = sections.value.map(section => section.key).filter(key => !order.includes(key))
    const hidden = new Set(activeDesign.value?.hiddenSections ?? [])
    return [...order, ...rest].map(key => ({ ...known.get(key)!, visible: !hidden.has(key) }))
  })

  const saver = createDraftSaveQueue({
    identity: () => identity,
    snapshot: () => {
      if (!conversation.value || !activeTemplate.value || !draft.value) return null
      return {
        conversationId: conversation.value.id,
        masterId: conversation.value.masterId,
        templateId: activeTemplate.value.templateId,
        variant: activeVariant.value,
        settings: cloneDesign(draft.value),
        version: version.value,
      }
    },
    recovery: {
      key: () => `jobproof:ai-resume:design:${identity}`,
      serialize: snapshot => JSON.stringify({ settings: snapshot.settings, variantCode: snapshot.variant }),
    },
    save: snapshot => saveAiResumeDesign(snapshot.conversationId, snapshot.templateId, snapshot.variant, snapshot.settings, snapshot.version),
    accept: async (saved, snapshot, context) => {
      if (!conversation.value) return
      version.value = saved.versionNo
      conversation.value.activeDesign = saved
      if (context.isLatest()) {
        draft.value = cloneDesign(saved.settings)
        variant.value = saved.variantCode
      }
      const index = session.templates.value.findIndex(item => item.templateId === saved.templateId)
      if (index >= 0) session.templates.value[index] = { ...session.templates.value[index]!, design: saved }
      try {
        const current = await fetchCurrentResumeLayout(snapshot.masterId)
        if (context.isCurrent() && conversation.value) conversation.value = { ...conversation.value, layout: current.layout ?? null }
      } catch {
        if (context.isCurrent()) session.fail('设计已保存，但 PDF 容量状态暂未刷新；重新打开工作台后会自动恢复。')
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
    identity = nextIdentity
    saver.reset()
    const settings = next.activeDesign?.settings ?? next.layout?.design ?? null
    draft.value = settings ? cloneDesign(settings) : null
    version.value = next.activeDesign?.versionNo ?? 0
    variant.value = next.activeDesign?.variantCode ?? next.layout?.variantCode ?? ''
    saveState.value = 'idle'
    try {
      const local = saver.readRecovery()
      if (local && settings) {
        const backup = JSON.parse(local) as { settings?: ResumeDesignSettings; variantCode?: string }
        if (backup.settings?.schemaVersion === settings.schemaVersion && typeof backup.variantCode === 'string'
          && Array.isArray(backup.settings.hiddenSections) && Array.isArray(backup.settings.sectionOrder)) {
          draft.value = cloneDesign({ ...settings, ...backup.settings })
          variant.value = backup.variantCode
          saver.reset(true)
          saveState.value = 'waiting'
        }
      }
    } catch {
      session.fail('无法读取本地设计副本，请确认浏览器允许本地存储。')
    }
  })

  function update<K extends keyof ResumeDesignSettings>(key: K, value: ResumeDesignSettings[K]): void {
    if (!activeDesign.value || templatePending.value || session.isDisposed()) return
    draft.value = { ...cloneDesign(activeDesign.value), [key]: value }
    saver.changed(DESIGN_DEBOUNCE_MS)
  }

  function changePreset(variantCode: string): void {
    const preset = activeTemplate.value?.presets.find(item => item.variantCode === variantCode)
    if (!preset || variantCode === activeVariant.value || templatePending.value || session.isDisposed()) return
    variant.value = variantCode
    draft.value = cloneDesign(preset.settings)
    saver.changed(DESIGN_DEBOUNCE_MS)
  }

  function toggleSection(key: string, visible: boolean): void {
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
    try {
      if (!(await persist()) || session.isDisposed() || conversation.value?.id !== conversationId) return
      const next = await selectAiResumeTemplate(conversationId, templateId, conversation.value.layout?.version)
      if (session.isDisposed() || conversation.value?.id !== conversationId) return
      session.applyConversation(next)
      await session.loadTemplates()
      session.notify('模板已切换，简历内容和内容版本保持不变。')
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
    activeDesign,
    layoutJson,
    rendererProtocol,
    activeVariant,
    accentColors,
    sections,
    orderedSections,
    isDirty: () => saver.dirty,
    update,
    changePreset,
    toggleSection,
    moveSection,
    persist,
    changeTemplate,
    dispose: () => saver.dispose(),
  }
}

export type DesignDraft = ReturnType<typeof useDesignDraft>

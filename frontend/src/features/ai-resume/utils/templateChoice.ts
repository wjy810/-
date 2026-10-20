/**
 * What the template detail page lets you pick before using a template — palette, fonts, header and
 * paper — carried into the workbench. "用此模板创建简历" passes the picks as query parameters to the
 * new-resume flow; both that flow and "应用到这份简历" then save them over the template's design.
 * Only the picks the user actually changed are carried, so an existing resume keeps the rest.
 */
import type { LocationQuery } from 'vue-router'
import type { ResumeDesignV2 } from '@/resume-render/theme/design'
import { coerceDesign, defaultDesign } from '@/resume-render/theme/tokens'
import { manifestOf } from '@/resume-render/templates/manifests'
import { saveAiResumeDesign, selectAiResumeTemplate } from '../services/aiResumeApi'
import type { AiResumeConversation } from '../types'

export type TemplateChoice = Partial<Pick<ResumeDesignV2, 'paletteId' | 'fontPairing' | 'headerVariant' | 'paperSize'>>

const QUERY_KEYS = { paletteId: 'palette', fontPairing: 'font', headerVariant: 'header', paperSize: 'paper' } as const
type ChoiceKey = keyof typeof QUERY_KEYS

/** Keeps only values the template offers. */
export function validChoice(templateId: string, choice: TemplateChoice): TemplateChoice {
  const manifest = manifestOf(templateId)
  if (!manifest) return {}
  const next: TemplateChoice = {}
  if (choice.paletteId && manifest.palettes.some(item => item.id === choice.paletteId)) next.paletteId = choice.paletteId
  if (choice.fontPairing && manifest.fontPairings.includes(choice.fontPairing)) next.fontPairing = choice.fontPairing
  if (choice.headerVariant && manifest.headerVariants.some(item => item.id === choice.headerVariant)) next.headerVariant = choice.headerVariant
  if (choice.paperSize && manifest.paperSizes.includes(choice.paperSize)) next.paperSize = choice.paperSize
  return next
}

export function choiceToQuery(choice: TemplateChoice): Record<string, string> {
  const query: Record<string, string> = {}
  for (const key of Object.keys(QUERY_KEYS) as ChoiceKey[]) {
    const value = choice[key]
    if (value) query[QUERY_KEYS[key]] = value
  }
  return query
}

export function choiceFromQuery(templateId: string, query: LocationQuery): TemplateChoice {
  const read = (key: ChoiceKey) => {
    const value = query[QUERY_KEYS[key]]
    return typeof value === 'string' ? value : undefined
  }
  return validChoice(templateId, {
    paletteId: read('paletteId'),
    fontPairing: read('fontPairing') as TemplateChoice['fontPairing'],
    headerVariant: read('headerVariant'),
    paperSize: read('paperSize') as TemplateChoice['paperSize'],
  })
}

export type TemplateChoiceResult = {
  conversation: AiResumeConversation
  /** False when the template was applied but the picks could not be saved (the user can redo them). */
  designSaved: boolean
}

/**
 * Switches the conversation to the template unless it already uses it, then saves the picks over that
 * template's design, keeping every other setting. A failed switch rejects; a failed design save does not.
 */
export async function applyTemplateChoice(conversation: AiResumeConversation, templateId: string,
  choice: TemplateChoice): Promise<TemplateChoiceResult> {
  let next = conversation
  if (conversation.layout?.templateId !== templateId) {
    next = await selectAiResumeTemplate(conversation.id, templateId, conversation.layout?.version)
  }
  const manifest = manifestOf(templateId)
  const picked = validChoice(templateId, choice)
  if (!manifest || !Object.keys(picked).length) return { conversation: next, designSaved: true }
  const stored = next.activeDesign?.templateId === templateId ? next.activeDesign : null
  const current = coerceDesign(manifest, stored?.settings ?? next.layout?.design ?? defaultDesign(manifest))
  // A palette picked on the detail page replaces a custom accent colour, as it does in the workbench.
  const settings = coerceDesign(manifest, { ...current, ...picked, ...(picked.paletteId ? { customAccent: null } : {}) })
  if (JSON.stringify(settings) === JSON.stringify(current)) return { conversation: next, designSaved: true }
  try {
    const saved = await saveAiResumeDesign(next.id, templateId, stored?.variantCode ?? 'DEFAULT', settings, stored?.versionNo ?? 0)
    return { conversation: { ...next, activeDesign: saved }, designSaved: true }
  } catch {
    return { conversation: next, designSaved: false }
  }
}

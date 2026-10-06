/**
 * Projects unconfirmed card drafts onto the confirmed resume so the A4 preview is live
 * while the formal resume and PDF stay untouched until the user confirms.
 */
import type { ResumeMasterView } from '@/features/resume/types'
import type { AiResumeConversation } from '../types'
import { applyPendingChangeOverlay, shouldOverlayCardPayload } from '../utils/changeSetOverlay'
import { cleanItems, formatStructuredItems, itemsOf, objectRecord } from './structured'

type Payloads = Record<string, Record<string, unknown> | undefined>
type IsDirty = (cardId: string) => boolean

export function buildPreviewResume(conversation: AiResumeConversation, payloads: Payloads, isDirty: IsDirty): ResumeMasterView {
  const resume = { ...conversation.resume }
  for (const card of conversation.cards) {
    if (!shouldOverlayCardPayload(card.status, isDirty(card.id))) continue
    const payload = payloads[card.id] ?? card.payload ?? {}
    if (card.cardType === 'EDUCATION') resume.education = formatStructuredItems(card.cardType, itemsOf(card.cardType, payloads[card.id]))
    else if (card.cardType === 'EXPERIENCE') resume.experience = formatStructuredItems(card.cardType, itemsOf(card.cardType, payloads[card.id]))
    else if (card.cardType === 'PROJECTS') resume.projects = formatStructuredItems(card.cardType, itemsOf(card.cardType, payloads[card.id]))
    else if (card.cardType === 'SKILLS') resume.skills = String(payload.text ?? '')
    else if (card.cardType === 'CERTIFICATES') resume.certificates = String(payload.text ?? '')
    else if (card.cardType === 'SUMMARY') resume.selfIntro = String(payload.text ?? '')
  }
  return resume
}

const CONTENT_KEYS: Record<string, string> = {
  EDUCATION: 'education', EXPERIENCE: 'experiences', PROJECTS: 'projects', ORGANIZATIONS: 'organizations',
  SKILLS: 'skills', CERTIFICATES: 'certificates', HONORS: 'honors', LANGUAGES: 'languages',
}

export function buildPreviewContent(conversation: AiResumeConversation, payloads: Payloads, isDirty: IsDirty): Record<string, unknown> {
  const content = JSON.parse(JSON.stringify(conversation.content ?? {})) as Record<string, unknown>
  for (const card of conversation.cards) {
    if (!shouldOverlayCardPayload(card.status, isDirty(card.id))) continue
    const payload = payloads[card.id] ?? card.payload ?? {}
    if (card.cardType === 'TARGET_JOB') content.intentions = { ...objectRecord(content.intentions), targetJob: payload.targetJob ?? '' }
    else if (card.cardType === 'CONTACT') content.basics = { ...objectRecord(content.basics), ...payload }
    else if (card.cardType === 'SUMMARY') content.summary = String(payload.text ?? '')
    else if (CONTENT_KEYS[card.cardType]) content[CONTENT_KEYS[card.cardType]!] = cleanItems(payload.items)
  }
  return applyPendingChangeOverlay(content, conversation.changeSets ?? [], conversation.activeBranchId)
}

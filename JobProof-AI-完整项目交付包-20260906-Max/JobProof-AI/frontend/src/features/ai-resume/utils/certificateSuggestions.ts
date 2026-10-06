import type { AiCertificateItemSuggestion, AiCertificateNameCandidate } from '../types'

export type CertificateDraftItem = Record<string, unknown>
export type CredentialKind = 'CERTIFICATE' | 'HONOR'
export const CREDENTIAL_DESCRIPTION_MIN_CHARACTERS = 60

export function isCredentialFactsInsufficientReason(reason: string): boolean {
  return reason === 'AI_CERTIFICATE_FACTS_INSUFFICIENT' || reason === 'AI_HONOR_FACTS_INSUFFICIENT'
}

export function hasCertificateFacts(items: CertificateDraftItem[]): boolean {
  return items.some((item) => text(item.name).length > 0)
}

export const hasCredentialFacts = hasCertificateFacts

export function mergeCertificateSuggestions(
  current: CertificateDraftItem[],
  incoming: AiCertificateItemSuggestion[],
): CertificateDraftItem[] {
  const result = current.filter(hasContent).map((item) => ({ ...item }))
  for (const certificate of incoming) {
    const index = result.findIndex((item) =>
      normalizeCertificateName(text(item.name)) === normalizeCertificateName(certificate.name))
    if (index < 0) {
      result.push({
        name: certificate.name.trim(),
        issuer: certificate.issuer.trim(),
        date: certificate.date.trim(),
        description: certificate.description.trim(),
      })
      continue
    }
    const existing = result[index]
    result[index] = {
      ...existing,
      name: text(existing.name) || certificate.name.trim(),
      issuer: text(existing.issuer) || certificate.issuer.trim(),
      date: text(existing.date) || certificate.date.trim(),
      description: mergeDescriptions(text(existing.description), certificate.description),
    }
  }
  return result.length ? result : [{ name: '', issuer: '', date: '', description: '' }]
}

export function mergeCredentialSuggestions(
  current: CertificateDraftItem[],
  incoming: AiCertificateItemSuggestion[],
  kind: CredentialKind,
): CertificateDraftItem[] {
  if (kind === 'CERTIFICATE') return mergeCertificateSuggestions(current, incoming)
  const result = current.filter(hasContent).map((item) => ({ ...item }))
  for (const honor of incoming) {
    const index = result.findIndex((item) =>
      normalizeCredentialName(text(item.name), kind) === normalizeCredentialName(honor.name, kind))
    if (index < 0) {
      result.push({
        name: honor.name.trim(), issuer: honor.issuer.trim(), date: honor.date.trim(),
        description: honor.description.trim(),
      })
      continue
    }
    const existing = result[index]
    result[index] = {
      ...existing,
      name: text(existing.name) || honor.name.trim(),
      issuer: text(existing.issuer) || honor.issuer.trim(),
      date: text(existing.date) || honor.date.trim(),
      description: mergeDescriptions(text(existing.description), honor.description),
    }
  }
  return result.length ? result : [{ name: '', issuer: '', date: '', description: '' }]
}

export function normalizeCertificateName(value: string): string {
  return text(value).toLowerCase().replace(/[\s\p{P}，、；：·（）【】《》®™]/gu, '').replace(/(?:证书|认证)$/u, '')
}

export function normalizeCredentialName(value: string, kind: CredentialKind): string {
  const normalized = text(value).toLowerCase().replace(/[\s\p{P}，、；：·（）【】《》®™]/gu, '')
  return kind === 'CERTIFICATE'
    ? normalized.replace(/(?:证书|认证)$/u, '')
    : normalized.replace(/(?:荣誉|奖项|称号)$/u, '')
}

export function credentialDescriptionCharacters(value: unknown): number {
  return Array.from(String(value ?? '')).filter((character) => /[\p{L}\p{N}]/u.test(character)).length
}

export function credentialDescriptionIssue(
  items: CertificateDraftItem[],
  label: string,
): string {
  for (let index = 0; index < items.length; index += 1) {
    const item = items[index]
    if (!hasContent(item)) continue
    if (!text(item.name)) return `${label}第 ${index + 1} 项必须填写名称。`
    if (!text(item.issuer)) {
      const institution = label.includes('证书') ? '颁发机构' : '授予机构'
      return `${label}第 ${index + 1} 项必须填写${institution}。`
    }
    const count = credentialDescriptionCharacters(item.description)
    if (count < CREDENTIAL_DESCRIPTION_MIN_CHARACTERS) {
      return `${label}第 ${index + 1} 项补充说明至少需要 ${CREDENTIAL_DESCRIPTION_MIN_CHARACTERS} 个有效字符，当前 ${count} 个。`
    }
  }
  return ''
}

export function mergeCredentialNameCandidates(
  current: AiCertificateNameCandidate[],
  incoming: AiCertificateNameCandidate[],
  kind: CredentialKind,
): AiCertificateNameCandidate[] {
  const result = current.map((candidate) => ({ ...candidate, sourceRefs: [...candidate.sourceRefs] }))
  for (const candidate of incoming) {
    const index = result.findIndex((value) =>
      normalizeCredentialName(value.name, kind) === normalizeCredentialName(candidate.name, kind))
    if (index < 0) {
      result.push({ ...candidate, sourceRefs: [...candidate.sourceRefs] })
    } else if (candidate.candidateType === 'OWNED' || result[index].candidateType !== 'OWNED') {
      result[index] = { ...candidate, sourceRefs: [...candidate.sourceRefs] }
    }
  }
  return result
}

function mergeDescriptions(existing: string, incoming: string): string {
  const next = incoming.trim()
  if (!existing) return next
  if (!next) return existing
  const known = new Set(existing.split(/\r?\n/).map(normalizeDescription).filter(Boolean))
  const additions = next.split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line && !known.has(normalizeDescription(line)))
  return additions.length ? `${existing}\n${additions.join('\n')}` : existing
}

function normalizeDescription(value: string): string {
  return value.toLowerCase().replace(/\s+/g, '').replace(/[•·*\-]/g, '')
}

function hasContent(item: CertificateDraftItem): boolean {
  return ['name', 'issuer', 'date', 'description'].some((key) => text(item[key]).length > 0)
}

function text(value: unknown): string {
  return String(value ?? '').trim()
}

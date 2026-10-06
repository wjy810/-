export type SummaryDraftChoice = {
  previous: string
  next: string
  changed: boolean
}

export function summaryVisibleCharacters(value: unknown): number {
  return Array.from(String(value ?? '').replace(/\s/g, '')).length
}

export function chooseSummaryDraft(currentSummary: string, candidateText: string): SummaryDraftChoice {
  const previous = String(currentSummary ?? '')
  const next = String(candidateText ?? '').trim()
  return { previous, next, changed: previous.trim() !== next }
}

export function distinctSummaryCandidates(values: Array<{ text: string }>): boolean {
  const normalized = values.map((value) => value.text.replace(/\s/g, '').toLocaleLowerCase())
  return normalized.length === new Set(normalized).size
}

export type UpdateFilterState = {
  q: string
  type: string
  module: string
  versionFrom: string
  versionTo: string
  range: string
  page: number
}

type QueryValue = string | Array<string | null> | null | undefined

function first(value: QueryValue): string {
  return Array.isArray(value) ? value[0] || '' : value || ''
}

export function parseUpdateFilters(query: Record<string, QueryValue>): UpdateFilterState {
  const parsedPage = Number.parseInt(first(query.page), 10)
  return {
    q: first(query.q).trim(),
    type: first(query.type),
    module: first(query.module),
    versionFrom: first(query.versionFrom),
    versionTo: first(query.versionTo),
    range: first(query.range),
    page: Number.isFinite(parsedPage) && parsedPage > 0 ? parsedPage : 0,
  }
}

export function serializeUpdateFilters(filters: UpdateFilterState): Record<string, string | undefined> {
  return {
    q: filters.q.trim() || undefined,
    type: filters.type || undefined,
    module: filters.module || undefined,
    versionFrom: filters.versionFrom || undefined,
    versionTo: filters.versionTo || undefined,
    range: filters.range || undefined,
    page: filters.page > 0 ? String(filters.page) : undefined,
  }
}

export function publishedFromForRange(range: string, now = Date.now()): string | undefined {
  const days = Number.parseInt(range, 10)
  if (!Number.isFinite(days) || days <= 0) return undefined
  return new Date(now - days * 86_400_000).toISOString()
}

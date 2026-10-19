export type Page<T> = { items: T[]; total: number }
export type Collected<T> = { items: T[]; total: number; truncated: boolean }

/**
 * Reads page after page (the API serves at most 100 per page) until everything is in or `max` items are
 * collected. `truncated` says that more exist than were read, so a picker can say so instead of silently
 * leaving the rest out.
 */
export async function collectPages<T>(
  load: (page: number, size: number) => Promise<Page<T>>,
  max = 500,
  size = 100,
): Promise<Collected<T>> {
  const items: T[] = []
  let total = 0
  for (let page = 0; items.length < max; page += 1) {
    const result = await load(page, size)
    total = result.total
    items.push(...result.items)
    if (result.items.length < size || items.length >= total) break
  }
  const kept = items.slice(0, max)
  return { items: kept, total, truncated: total > kept.length }
}

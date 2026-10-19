/** One slot in a pager: a 0-based page index, or a gap standing for the pages left out. */
export type PageSlot = number | 'gap-start' | 'gap-end'

/**
 * The page buttons to show for {@code page} of {@code count}: every page when there are at most seven,
 * otherwise the first, the last and the current one with a neighbour on each side, with gaps between.
 * The list always has the same length once it is windowed, so the buttons don't jump as the user pages.
 */
export function pageSlots(page: number, count: number): PageSlot[] {
  if (count <= 7) return Array.from({ length: Math.max(count, 0) }, (_, index) => index)
  const current = Math.min(Math.max(page, 0), count - 1)
  if (current <= 3) return [0, 1, 2, 3, 4, 'gap-end', count - 1]
  if (current >= count - 4) return [0, 'gap-start', count - 5, count - 4, count - 3, count - 2, count - 1]
  return [0, 'gap-start', current - 1, current, current + 1, 'gap-end', count - 1]
}

/** Pages needed for {@code total} items at {@code size} per page; never less than one. */
export function pageCountOf(total: number, size: number): number {
  return Math.max(1, Math.ceil(Math.max(total, 0) / Math.max(size, 1)))
}

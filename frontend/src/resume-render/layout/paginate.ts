/**
 * Pure page packing (docs/phase2/03 §3.3). Each region flows independently; a block that does not fit
 * moves to the next page, a block taller than a whole page is placed alone and reported as oversize.
 * No imports, so it runs under the Node logic-test runner.
 */

export interface MeasuredBlock {
  id: string
  region: string
  height: number
  section: string
}

export interface RegionCapacity {
  /** Usable height per region on page 1 (after the header). */
  first: Record<string, number>
  /** Usable height per region on continuation pages. */
  next: Record<string, number>
}

export interface PaginationResult {
  /** pages[i][region] = block ids, in order. */
  pages: Array<Record<string, string[]>>
  pageCount: number
  /** Height (px) that sits beyond the page limit, 0 when the content fits. */
  overflowPx: number
  firstOverflowSection: string | null
  oversize: string[]
  /** How full the last page is (0–1): the fullest region's used height over its capacity. */
  lastPageFill: number
}

/** A last page filled less than this is "nearly empty": the AUTO page target tries to fold it back. */
export const SPARSE_LAST_PAGE = 0.4

/**
 * AUTO page target: the page count worth tightening the spacing for, or null when the layout is fine
 * as it is. Content beyond the limit should come back within it; a nearly empty last page should fold
 * into the page before it.
 */
export function autoFitTarget(result: Pick<PaginationResult, 'pageCount' | 'lastPageFill'>, pageLimit: number): number | null {
  if (result.pageCount > pageLimit) return pageLimit
  if (result.pageCount > 1 && result.lastPageFill < SPARSE_LAST_PAGE) return result.pageCount - 1
  return null
}

export function paginate(blocks: MeasuredBlock[], capacity: RegionCapacity, pageLimit: number): PaginationResult {
  const regions = [...new Set(blocks.map(block => block.region))]
  const placement = new Map<string, number>()
  const oversize: string[] = []
  /** Where each region ends: its last page and the share of that page it fills. */
  const ends: Array<{ page: number; fill: number }> = []
  let pageCount = 1

  for (const region of regions) {
    let page = 0
    let used = 0
    for (const block of blocks) {
      if (block.region !== region) continue
      const cap = (page === 0 ? capacity.first[region] : capacity.next[region]) ?? 0
      if (used > 0 && used + block.height > cap + 0.5) {
        page += 1
        used = 0
      }
      const pageCap = (page === 0 ? capacity.first[region] : capacity.next[region]) ?? 0
      if (block.height > pageCap + 0.5) oversize.push(block.id)
      placement.set(block.id, page)
      used += block.height
      pageCount = Math.max(pageCount, page + 1)
    }
    const endCap = (page === 0 ? capacity.first[region] : capacity.next[region]) ?? 0
    ends.push({ page, fill: endCap > 0 ? Math.min(1, used / endCap) : 1 })
  }
  const lastPageFill = Math.max(0, ...ends.filter(end => end.page === pageCount - 1).map(end => end.fill))

  const pages: Array<Record<string, string[]>> = Array.from({ length: pageCount }, () => Object.fromEntries(regions.map(region => [region, [] as string[]])))
  for (const block of blocks) pages[placement.get(block.id) ?? 0]![block.region]!.push(block.id)

  let overflowPx = 0
  let firstOverflowSection: string | null = null
  if (pageCount > pageLimit) {
    for (const region of regions) {
      const beyond = blocks.filter(block => block.region === region && (placement.get(block.id) ?? 0) >= pageLimit)
      const height = beyond.reduce((sum, block) => sum + block.height, 0)
      if (height > overflowPx) {
        overflowPx = height
        firstOverflowSection = beyond[0]?.section ?? null
      }
    }
  }
  return { pages, pageCount, overflowPx, firstOverflowSection, oversize, lastPageFill }
}

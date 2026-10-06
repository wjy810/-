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
}

export function paginate(blocks: MeasuredBlock[], capacity: RegionCapacity, pageLimit: number): PaginationResult {
  const regions = [...new Set(blocks.map(block => block.region))]
  const placement = new Map<string, number>()
  const oversize: string[] = []
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
  }

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
  return { pages, pageCount, overflowPx, firstOverflowSection, oversize }
}

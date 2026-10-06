import { resumeTimelineEntryText, type ResumeTimelineEntry } from './resumeStructuredEntries.ts'

export type PreviewPaginationSection = {
  key: string
  label: string
  value: string
  headingStyle: string
  entries?: ResumeTimelineEntry[]
  order?: number
}

export type PreviewPaginationColumn = {
  id: string
  widthPercent: number
  slotKeys: string[]
  tone?: string
  sections: PreviewPaginationSection[]
}

export type PreviewPaginationPage = {
  maxPages?: number
  capacityUnits?: number
  density?: string
  fontScale?: string
  lineHeight?: string
}

export function paginatePreviewColumns(
  columns: PreviewPaginationColumn[],
  page: PreviewPaginationPage | undefined,
): PreviewPaginationColumn[][] {
  const maxPages = Math.max(1, page?.maxPages ?? 1)
  const pageCapacity = Math.max(1, Math.round((page?.capacityUnits ?? 3600)
    * capacityRatio(columns, page)))
  const balancedColumns = balanceColumnsForSinglePage(columns, pageCapacity)
  if (maxPages === 1) return [balancedColumns]

  const columnPageCounts = balancedColumns.map((column) => {
    const units = column.sections.reduce((total, section) => total + estimatedUnits(section.value), 0)
    const capacity = Math.max(1, Math.round(pageCapacity * column.widthPercent / 100))
    return Math.min(maxPages, Math.max(1, Math.ceil(units / capacity)))
  })
  const pageCount = Math.max(1, ...columnPageCounts)
  if (pageCount === 1) return [balancedColumns]

  const partitioned = balancedColumns.map((column, index) => partitionColumn(
    column,
    Math.max(1, Math.round(pageCapacity * column.widthPercent / 100)),
    columnPageCounts[index] ?? 1,
  ))
  return Array.from({ length: pageCount }, (_, pageIndex) => {
    const pageColumns = balancedColumns.map((column, columnIndex) => ({
      ...column,
      sections: partitioned[columnIndex]?.[pageIndex] ?? [],
    }))
    if (pageIndex === 0) return pageColumns
    const activeColumns = pageColumns.filter((column) => column.sections.length > 0)
    if (activeColumns.length === 0 || activeColumns.length === pageColumns.length) return pageColumns
    const activeWidth = activeColumns.reduce((total, column) => total + column.widthPercent, 0)
    return activeColumns.map((column) => ({
      ...column,
      widthPercent: activeColumns.length === 1
        ? 100
        : column.widthPercent / activeWidth * 100,
    }))
  })
}

function capacityRatio(
  columns: PreviewPaginationColumn[],
  page: PreviewPaginationPage | undefined,
): number {
  let ratio = columns.length === 1 ? .82 : 1
  const density = (page?.density ?? 'STANDARD').toUpperCase()
  const fontScale = (page?.fontScale ?? 'STANDARD').toUpperCase()
  const lineHeight = (page?.lineHeight ?? 'STANDARD').toUpperCase()
  if (density === 'STANDARD') ratio *= .9
  if (density === 'AIRY') ratio *= .76
  if (fontScale === 'LARGE') ratio *= .86
  if (lineHeight === 'AIRY') ratio *= .86
  return Math.max(.52, ratio)
}

function balanceColumnsForSinglePage(
  columns: PreviewPaginationColumn[],
  pageCapacity: number,
): PreviewPaginationColumn[] {
  if (columns.length < 2) return columns
  const original = columns.map((column) => ({ ...column, sections: [...column.sections] }))
  const units = new Map(original.flatMap((column) => column.sections)
    .map((section) => [section.key, estimatedUnits(section.value)]))
  const totalUnits = [...units.values()].reduce((total, value) => total + value, 0)
  if (totalUnits > pageCapacity) return columns

  const result = original.map((column) => ({ ...column, sections: [...column.sections] }))
  const order = new Map(result.flatMap((column) => column.sections)
    .sort((left, right) => (left.order ?? Number.MAX_SAFE_INTEGER) - (right.order ?? Number.MAX_SAFE_INTEGER))
    .map((section, index) => [section.key, section.order ?? index]))
  const maxMoves = units.size * Math.max(1, result.length - 1)

  for (let move = 0; move < maxMoves; move += 1) {
    const loads = columnLoads(result, units, pageCapacity)
    const currentMax = Math.max(...loads)
    if (currentMax <= 1) break
    const sourceIndex = loads.indexOf(currentMax)
    let best: { sectionIndex: number; targetIndex: number; maxLoad: number } | null = null

    for (let priority = 0; priority <= 4 && !best; priority += 1) {
      for (let sectionIndex = 0; sectionIndex < result[sourceIndex]!.sections.length; sectionIndex += 1) {
        const section = result[sourceIndex]!.sections[sectionIndex]!
        if (movementPriority(section.key) !== priority || (units.get(section.key) ?? 0) === 0) continue
        for (let targetIndex = 0; targetIndex < result.length; targetIndex += 1) {
          if (targetIndex === sourceIndex) continue
          const nextLoads = [...loads]
          const sectionUnits = units.get(section.key) ?? 0
          nextLoads[sourceIndex] -= sectionUnits
            / Math.max(1, pageCapacity * result[sourceIndex]!.widthPercent / 100)
          nextLoads[targetIndex] += sectionUnits
            / Math.max(1, pageCapacity * result[targetIndex]!.widthPercent / 100)
          const nextMax = Math.max(...nextLoads)
          if (nextMax >= currentMax - .001) continue
          if (!best || nextMax < best.maxLoad) best = { sectionIndex, targetIndex, maxLoad: nextMax }
        }
      }
    }
    if (!best) break
    const [section] = result[sourceIndex]!.sections.splice(best.sectionIndex, 1)
    if (section) result[best.targetIndex]!.sections.push(section)
  }

  if (Math.max(...columnLoads(result, units, pageCapacity)) > 1) return columns
  result.forEach((column) => column.sections.sort((left, right) =>
    (order.get(left.key) ?? Number.MAX_SAFE_INTEGER) - (order.get(right.key) ?? Number.MAX_SAFE_INTEGER)))
  return result
}

function columnLoads(
  columns: PreviewPaginationColumn[],
  units: Map<string, number>,
  pageCapacity: number,
): number[] {
  return columns.map((column) => {
    const used = column.sections.reduce((total, section) => total + (units.get(section.key) ?? 0), 0)
    return used / Math.max(1, pageCapacity * column.widthPercent / 100)
  })
}

function movementPriority(key: string): number {
  if (['organizations', 'honors', 'languages'].includes(key)) return 0
  if (key === 'certificates') return 1
  if (['education', 'skills'].includes(key)) return 2
  if (['summary', 'projects'].includes(key)) return 3
  return 4
}

export function estimatedUnits(value: string): number {
  const text = value.trim()
  if (!text) return 0
  const lineBreaks = (text.match(/\n/g) ?? []).length
  return Array.from(text).length + lineBreaks * 12 + 36
}

function partitionColumn(
  column: PreviewPaginationColumn,
  pageCapacity: number,
  pageCount: number,
): PreviewPaginationSection[][] {
  if (pageCount <= 1) return [column.sections]
  const result = Array.from({ length: pageCount }, () => [] as PreviewPaginationSection[])
  let pageIndex = 0
  let usedUnits = 0

  for (const section of column.sections) {
    if (section.entries?.length) {
      const groups = splitEntries(section.entries, Math.max(80, pageCapacity - 48), pageCount)
      for (let groupIndex = 0; groupIndex < groups.length; groupIndex += 1) {
        const entries = groups[groupIndex] ?? []
        const value = entries.map(resumeTimelineEntryText).join('\n\n')
        const chunkSection = {
          ...section,
          label: groupIndex === 0 ? section.label : `${section.label}（续）`,
          value,
          entries,
        }
        const chunkUnits = estimatedUnits(value)
        if (pageIndex < pageCount - 1 && usedUnits > 0 && usedUnits + chunkUnits > pageCapacity) {
          pageIndex += 1
          usedUnits = 0
        }
        result[pageIndex]?.push(chunkSection)
        usedUnits += chunkUnits
      }
      continue
    }
    let remaining = section.value.trim()
    let chunkIndex = 0
    while (remaining) {
      if (pageIndex < pageCount - 1 && usedUnits > 0 && pageCapacity - usedUnits < 80) {
        pageIndex += 1
        usedUnits = 0
      }
      const available = pageIndex < pageCount - 1
        ? Math.max(80, pageCapacity - usedUnits)
        : Number.MAX_SAFE_INTEGER
      const [chunk, tail] = splitTextByUnits(remaining, available)
      const chunkSection = {
        ...section,
        label: chunkIndex === 0 ? section.label : `${section.label}（续）`,
        value: chunk,
      }
      const chunkUnits = estimatedUnits(chunk)
      result[pageIndex]?.push(chunkSection)
      usedUnits += chunkUnits
      remaining = tail
      chunkIndex += 1
      if (remaining && pageIndex < pageCount - 1) {
        pageIndex += 1
        usedUnits = 0
      }
    }
  }
  return result
}

function splitTextByUnits(value: string, limit: number): [string, string] {
  const characters = Array.from(value.trim())
  if (!characters.length || estimatedUnits(value) <= limit) return [characters.join(''), '']

  let units = 48
  let boundary = 0
  while (boundary < characters.length) {
    const nextUnits = 1 + (characters[boundary] === '\n' ? 32 : 0)
    if (units + nextUnits > limit) break
    units += nextUnits
    boundary += 1
  }
  if (boundary <= 0) boundary = Math.min(characters.length, Math.max(1, limit - 48))
  const lowerBound = Math.max(1, Math.floor(boundary * .62))
  for (let index = boundary; index >= lowerBound; index -= 1) {
    if (/\s|[，。；、]/.test(characters[index - 1] ?? '')) {
      boundary = index
      break
    }
  }
  return [
    characters.slice(0, boundary).join('').trim(),
    characters.slice(boundary).join('').trim(),
  ]
}

function splitEntries(entries: ResumeTimelineEntry[], limit: number, maxChunks: number): ResumeTimelineEntry[][] {
  if (maxChunks <= 1) return [entries]
  const result: ResumeTimelineEntry[][] = [[]]
  let usedUnits = 0
  for (const entry of entries) {
    const units = estimatedUnits(resumeTimelineEntryText(entry))
    const current = result[result.length - 1] ?? []
    if (current.length && result.length < maxChunks && usedUnits + units > limit) {
      result.push([entry])
      usedUnits = units
    } else {
      current.push(entry)
      usedUnits += units
    }
  }
  return result.filter((group) => group.length)
}

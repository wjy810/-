/** Orders of the resume list; the choice is remembered per browser. */
export const RESUME_SORTS = [
  { value: 'updated-desc', label: '最近更新' },
  { value: 'updated-asc', label: '最早更新' },
  { value: 'title', label: '按名称' },
] as const

export type ResumeSort = (typeof RESUME_SORTS)[number]['value']

/** Chinese names by pinyin, numbers by value ("简历 2" before "简历 10"). */
const collator = new Intl.Collator('zh-CN', { numeric: true, sensitivity: 'base' })

export function isResumeSort(value: unknown): value is ResumeSort {
  return RESUME_SORTS.some(item => item.value === value)
}

/** A sorted copy; ties keep the most recently updated first. */
export function sortResumes<T extends { title: string; updatedAt?: string | null }>(items: readonly T[], sort: ResumeSort): T[] {
  const newest = (a: T, b: T) => (b.updatedAt ?? '').localeCompare(a.updatedAt ?? '')
  const compare = sort === 'title'
    ? (a: T, b: T) => collator.compare(a.title, b.title) || newest(a, b)
    : sort === 'updated-asc'
      ? (a: T, b: T) => -newest(a, b)
      : newest
  return [...items].sort(compare)
}

type Grouped = { kind: 'MASTER' | 'VERSION'; masterId: string; title: string; updatedAt?: string | null }

/**
 * Each resume followed by its own versions (newest first), resumes in the chosen order. Versions
 * whose resume is not in the list keep their sorted place at the end.
 */
export function groupUnderMasters<T extends Grouped>(items: readonly T[], sort: ResumeSort): T[] {
  const masters = sortResumes(items.filter(item => item.kind === 'MASTER'), sort)
  const versions = sortResumes(items.filter(item => item.kind === 'VERSION'), 'updated-desc')
  const byMaster = new Map<string, T[]>()
  for (const version of versions) byMaster.set(version.masterId, [...(byMaster.get(version.masterId) ?? []), version])
  const grouped: T[] = []
  for (const master of masters) {
    grouped.push(master, ...(byMaster.get(master.masterId) ?? []))
    byMaster.delete(master.masterId)
  }
  for (const orphans of byMaster.values()) grouped.push(...orphans)
  return grouped
}

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

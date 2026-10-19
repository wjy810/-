/**
 * The resume list's rows: one per resume and one per tailored or frozen version, with the labels,
 * tones and links the card and table views share. Pure, so it runs under the logic-test runner.
 */
import type { BadgeTone } from '../../../shared/ui/UiBadge.vue'
import type { JobProofIconName } from '../../../shared/ui/jobProofIcons'
import { glossMasterStatus, glossVersionStatus, outcomeResolved, versionOrdinals } from '../labels.ts'
import type { KeyOutcome, ResumeMasterSummary, ResumeMasterView, ResumeVersionView } from '../types'

export type RowType = '主简历' | '定制版' | '冻结版本'
export type RowTone = 'blue' | 'green' | 'orange'
/** The row-type badge colour: resume, tailored version, frozen version. */
export const ROW_BADGE_TONE: Record<RowTone, BadgeTone> = { blue: 'primary', green: 'success', orange: 'accent' }
export type StatusTone = 'green' | 'orange' | 'red' | 'gray' | 'blue'

export type ResumeRow = {
  key: string
  kind: 'MASTER' | 'VERSION'
  masterId: string
  versionId: string
  title: string
  /** 1, 2, 3… by creation order within its resume; masters have none. */
  ordinal: number | null
  rowType: RowType
  rowTone: RowTone
  rowIcon: JobProofIconName
  statusLabel: string
  statusTone: StatusTone
  statusSub: string
  coverage: number | null
  updatedAt?: string | null
  archived: boolean
  lockNote: boolean
  pendingConfirm: boolean
  expectedVersion: number
}

export type ResumeRows = { rows: ResumeRow[]; detailGap: number }

function coverageOf(outcomes?: KeyOutcome[] | null): number | null {
  const items = (outcomes ?? []).filter((item) => (item.text ?? '').trim())
  if (!items.length) {
    return null
  }
  const done = items.filter((item) => outcomeResolved(item)).length
  return Math.round((done / items.length) * 100)
}

export function coverageSub(value: number): string {
  if (value >= 80) {
    return '覆盖充分'
  }
  if (value >= 60) {
    return '覆盖良好'
  }
  return '待补充'
}

function snapshotOutcomes(version: ResumeVersionView): KeyOutcome[] {
  const raw = version.snapshot?.keyOutcomes
  return Array.isArray(raw) ? (raw as KeyOutcome[]) : []
}

function versionTitle(item: ResumeMasterSummary, version: ResumeVersionView): string {
  const raw = version.snapshot?.title
  if (typeof raw === 'string' && raw.trim()) {
    return raw.trim()
  }
  return `${item.title?.trim() || '未命名简历'} · 快照`
}

export function masterRow(item: ResumeMasterSummary, detail: ResumeMasterView | null): ResumeRow {
  const archived = item.status === 'ARCHIVED'
  const pendingConfirm = item.status === 'PENDING_CONFIRMATION'
  return {
    key: `m-${item.id}`,
    kind: 'MASTER',
    masterId: item.id,
    versionId: '',
    title: item.title?.trim() || '未命名简历',
    ordinal: null,
    rowType: '主简历',
    rowTone: 'blue',
    rowIcon: 'resume-master-resume',
    statusLabel: glossMasterStatus(item.status),
    statusTone: archived ? 'gray' : pendingConfirm ? 'orange' : 'green',
    statusSub: archived ? '恢复后可编辑' : pendingConfirm ? '有待确认的内容' : '可编辑',
    coverage: coverageOf(detail?.keyOutcomes),
    updatedAt: item.updatedAt,
    archived,
    lockNote: false,
    pendingConfirm,
    expectedVersion: item.version,
  }
}

export function versionRow(item: ResumeMasterSummary, version: ResumeVersionView, ordinal: number | null): ResumeRow {
  const archived = version.status === 'ARCHIVED'
  const isSnapshot =
    version.status === 'FROZEN' || (archived && version.immutable)
  const pendingConfirm = version.status === 'PENDING_USER_CONFIRMATION'
  return {
    key: `v-${version.id}`,
    kind: 'VERSION',
    masterId: item.id,
    versionId: version.id,
    title: versionTitle(item, version),
    ordinal,
    rowType: isSnapshot ? '冻结版本' : '定制版',
    rowTone: isSnapshot ? 'orange' : 'green',
    rowIcon: isSnapshot ? 'resume-frozen-version' : 'resume-tailored-resume',
    statusLabel: glossVersionStatus(version.status),
    statusTone: archived
      ? 'gray'
      : version.status === 'FROZEN'
          ? 'red'
          : pendingConfirm
            ? 'orange'
            : 'gray',
    statusSub: version.immutable || isSnapshot ? '不可修改' : '确认后冻结',
    coverage: coverageOf(snapshotOutcomes(version)),
    updatedAt: version.frozenAt || version.createdAt,
    archived,
    lockNote: version.immutable,
    pendingConfirm,
    expectedVersion: version.version,
  }
}

export function rowLink(row: ResumeRow): string {
  if (row.kind === 'VERSION') {
    return `/resumes/${row.masterId}?versionId=${row.versionId}`
  }
  return row.archived ? `/resumes/${row.masterId}/manual` : `/resumes/${row.masterId}`
}

export function rowAction(row: ResumeRow): string {
  if (row.kind === 'MASTER') {
    return row.archived ? '打开' : '编辑'
  }
  return row.pendingConfirm ? '去确认' : '查看版本'
}

/** Rows for each resume and its versions; a resume whose details failed to load still gets its own row. */
export function buildRows(masters: readonly ResumeMasterSummary[], details: ReadonlyArray<ResumeMasterView | null>): ResumeRows {
  const rows: ResumeRow[] = []
  let detailGap = 0
  masters.forEach((master, index) => {
    const detail = details[index] ?? null
    rows.push(masterRow(master, detail))
    if (!detail) {
      detailGap += 1
      return
    }
    const versions = detail.versions ?? []
    const ordinals = versionOrdinals(versions)
    for (const version of versions) rows.push(versionRow(master, version, ordinals.get(version.id) ?? null))
  })
  return { rows, detailGap }
}

import { careerCanvasNodeStatusLabel, careerCanvasNodeTypeLabel } from './presentation.ts'

export type ProposalFieldChange = { field: string; label: string; before: string; after: string }

type Snapshot = Record<string, unknown>
type Operation = 'ADD' | 'UPDATE' | 'DELETE' | 'MOVE' | 'ADD_RELATION'

const DETAIL_FIELDS: Array<[string, string]> = [
  ['summary', '摘要'], ['learningContents', '学习内容'], ['masteryCriteria', '掌握标准'],
  ['estimatedHours', '预计学时'], ['importance', '重要度'],
]
const IMPORTANCE: Record<string, string> = { HIGH: '高', MEDIUM: '中', LOW: '低' }

function detailOf(snapshot: Snapshot): Snapshot {
  const value = snapshot.detail
  return value && typeof value === 'object' && !Array.isArray(value) ? value as Snapshot : {}
}

function text(field: string, value: unknown): string {
  if (value == null) return ''
  if (Array.isArray(value)) return value.filter(item => typeof item === 'string' && item.trim()).map(item => (item as string).trim()).join('；')
  if (typeof value === 'object') return ''
  if (field === 'estimatedHours') return typeof value === 'number' && value > 0 ? `${value} 小时` : ''
  if (field === 'importance') return IMPORTANCE[String(value)] ?? String(value)
  if (field === 'type') return careerCanvasNodeTypeLabel(String(value))
  if (field === 'status') return careerCanvasNodeStatusLabel(String(value))
  return String(value).trim()
}

/**
 * What one AI proposal item actually changes, field by field: before is the node as it is now, after is what the
 * model proposed. A field the proposal leaves out is not a change. Relations and deletions have no fields to
 * compare; parent moves are named by the caller, which knows the node titles.
 */
export function proposalFieldChanges(operation: Operation, before: Snapshot, after: Snapshot,
  parent?: { before: string; after: string }): ProposalFieldChange[] {
  if (operation === 'ADD_RELATION' || operation === 'DELETE') return []
  const changes: ProposalFieldChange[] = []
  const compare = (field: string, label: string, previous: unknown, next: unknown): void => {
    const nextText = text(field, next)
    const previousText = operation === 'ADD' ? '' : text(field, previous)
    if (nextText && nextText !== previousText) changes.push({ field, label, before: previousText, after: nextText })
  }
  compare('title', '名称', before.title, after.title)
  compare('type', '类型', before.type, after.nodeType)
  compare('status', '状态', before.status, after.status)
  if (parent && parent.after && parent.after !== parent.before) {
    changes.push({ field: 'parent', label: '上级', before: operation === 'ADD' ? '' : parent.before, after: parent.after })
  }
  const previousDetail = detailOf(before)
  const nextDetail = detailOf(after)
  for (const [field, label] of DETAIL_FIELDS) compare(field, label, previousDetail[field], nextDetail[field])
  return changes
}

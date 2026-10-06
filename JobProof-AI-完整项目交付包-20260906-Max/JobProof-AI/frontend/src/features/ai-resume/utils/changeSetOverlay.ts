import type { AiResumeChangeItem, AiResumeChangeSet } from '../types'

export function shouldOverlayCardPayload(status: string, locallyDirty: boolean): boolean {
  return locallyDirty || status === 'EDITING'
}

export function applyPendingChangeOverlay(
  source: Record<string, unknown>,
  changeSets: AiResumeChangeSet[],
  activeBranchId: string,
): Record<string, unknown> {
  const content = JSON.parse(JSON.stringify(source ?? {})) as Record<string, unknown>
  for (const changeSet of changeSets) {
    if (changeSet.branchId !== activeBranchId) continue
    for (const item of changeSet.items) {
      if (item.status !== 'PENDING') continue
      applyItem(content, item)
    }
  }
  return content
}

function applyItem(content: Record<string, unknown>, item: AiResumeChangeItem): void {
  const current = readText(content, item.targetPath)
  const before = String(item.beforeValue ?? '')
  const proposed = String(item.correctedValue ?? item.proposedValue ?? '')
  if (!proposed.trim()) return
  if (item.operation === 'REPLACE_TEXT') {
    if (current === before) setText(content, item.targetPath, proposed)
    return
  }
  const segments = splitSegments(current)
  if (item.operation === 'REPLACE_SEGMENT') {
    const index = segments.indexOf(before)
    if (index < 0) return
    segments[index] = proposed
  } else if (item.operation === 'APPEND_SEGMENT') {
    if (segments.includes(proposed)) return
    segments.push(proposed)
  } else return
  setText(content, item.targetPath, joinSegments(segments))
}

function readText(content: Record<string, unknown>, pointer: string): string {
  let value: unknown = content
  for (const part of parts(pointer)) {
    if (Array.isArray(value)) value = value[Number(part)]
    else if (value && typeof value === 'object') value = (value as Record<string, unknown>)[part]
    else return ''
  }
  return typeof value === 'string' ? value : ''
}

function setText(content: Record<string, unknown>, pointer: string, next: string): void {
  const path = parts(pointer)
  let parent: unknown = content
  for (const part of path.slice(0, -1)) {
    if (Array.isArray(parent)) parent = parent[Number(part)]
    else if (parent && typeof parent === 'object') parent = (parent as Record<string, unknown>)[part]
    else return
  }
  const leaf = path.at(-1)
  if (!leaf) return
  if (Array.isArray(parent)) parent[Number(leaf)] = next
  else if (parent && typeof parent === 'object') (parent as Record<string, unknown>)[leaf] = next
}

function parts(pointer: string): string[] {
  return pointer.replace(/^\//, '').split('/').filter(Boolean)
}

function splitSegments(value: string): string[] {
  return String(value ?? '').replace(/\r/g, '').split(/\n+/)
    .map((line) => line.replace(/^[\s•·●▪-]+/, '').trim()).filter(Boolean)
}

function joinSegments(values: string[]): string {
  return values.filter(Boolean).map((value) => `• ${value.trim()}`).join('\n')
}

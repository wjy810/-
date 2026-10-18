/**
 * A local copy of each unsaved card draft (docs/phase2/00 B-4). It outlives a closed tab or a dropped
 * connection, and turns a version conflict into a choice instead of one side silently winning.
 * Storage may be missing or throw (private mode, quota): every access degrades to "no mirror".
 */
export type MirroredDraft = {
  payload: Record<string, unknown>
  /** Card version the draft was typed against. */
  baseVersion: number
  savedAt: string
}

export type MirrorDecision = 'drop' | 'resume' | 'conflict'

/** Listed in shared/lib/localDrafts.ts, which clears these on sign-out. */
const PREFIX = 'jp:card-draft:'
const MAX_AGE_MS = 14 * 24 * 60 * 60 * 1000

export function draftMirrorKey(conversationId: string, branchId: string, cardId: string): string {
  return `${PREFIX}${conversationId}:${branchId}:${cardId}`
}

/** Where a draft the server refused is kept, apart from the draft still being typed. */
export function conflictMirrorKey(draftKey: string): string {
  return `${draftKey}:conflict`
}

export function localDraftStorage(): Storage | null {
  try {
    return typeof window === 'undefined' ? null : window.localStorage
  } catch {
    return null
  }
}

export function readDraftMirror(storage: Storage | null, key: string): MirroredDraft | null {
  if (!storage) return null
  try {
    const raw = storage.getItem(key)
    if (!raw) return null
    const value = JSON.parse(raw) as Partial<MirroredDraft> | null
    if (!value || typeof value.payload !== 'object' || value.payload === null || typeof value.baseVersion !== 'number') return null
    return { payload: value.payload, baseVersion: value.baseVersion, savedAt: String(value.savedAt ?? '') }
  } catch {
    return null
  }
}

export function writeDraftMirror(storage: Storage | null, key: string, draft: MirroredDraft): void {
  try { storage?.setItem(key, JSON.stringify(draft)) } catch { /* full or blocked: the server copy still applies */ }
}

export function removeDraftMirror(storage: Storage | null, key: string): void {
  try { storage?.removeItem(key) } catch { /* nothing to clean */ }
}

/**
 * What a mirrored draft means once the server's copy of the card is known: nothing (same content or
 * too old), unsent edits to pick up again (typed against the current version), or a conflict.
 */
export function reconcileDraftMirror(
  mirror: MirroredDraft,
  serverVersion: number,
  serverPayload: Record<string, unknown>,
  now = Date.now(),
): MirrorDecision {
  const age = now - Date.parse(mirror.savedAt)
  if (!Number.isFinite(age) || age > MAX_AGE_MS) return 'drop'
  if (samePayload(mirror.payload, serverPayload)) return 'drop'
  return mirror.baseVersion === serverVersion ? 'resume' : 'conflict'
}

/** Same content, ignoring key order and empty fields (a blank new record is not an edit). */
export function samePayload(left: unknown, right: unknown): boolean {
  return stable(prune(left)) === stable(prune(right))
}

function prune(value: unknown): unknown {
  if (Array.isArray(value)) {
    const items = value.map(prune).filter(item => item !== undefined)
    return items.length ? items : undefined
  }
  if (value && typeof value === 'object') {
    const entries = Object.entries(value as Record<string, unknown>)
      .map(([key, item]) => [key, prune(item)] as const)
      .filter(([, item]) => item !== undefined)
    return entries.length ? Object.fromEntries(entries) : undefined
  }
  if (value === null || value === undefined) return undefined
  if (typeof value === 'string') return value.trim() ? value : undefined
  return value
}

function stable(value: unknown): string {
  if (Array.isArray(value)) return `[${value.map(stable).join(',')}]`
  if (value && typeof value === 'object') {
    const record = value as Record<string, unknown>
    return `{${Object.keys(record).sort().map(key => `${JSON.stringify(key)}:${stable(record[key])}`).join(',')}}`
  }
  return JSON.stringify(value ?? null)
}

type SaveState = 'waiting' | 'saving' | 'saved' | 'error'
type RecoveryOperation = 'read' | 'write' | 'remove'

export type DraftSaveContext = {
  isCurrent: () => boolean
  isLatest: () => boolean
}

type DraftSaveOptions<Snapshot, Saved> = {
  identity: () => string
  snapshot: () => Snapshot | null
  save: (snapshot: Snapshot) => Promise<Saved>
  accept: (saved: Saved, snapshot: Snapshot, context: DraftSaveContext) => void | Promise<void>
  recovery: { key: () => string; serialize: (snapshot: Snapshot) => string }
  canSave?: () => boolean
  onState: (state: SaveState) => void
  onError: (reason: unknown) => void
  onRecoveryError: (operation: RecoveryOperation) => void
}

// One acknowledged server version feeds the next edit; local revisions never move backwards.
export function createDraftSaveQueue<Snapshot, Saved>(options: DraftSaveOptions<Snapshot, Saved>) {
  let revision = 0
  let confirmedRevision = 0
  let generation = 0
  let disposed = false
  let timer: ReturnType<typeof setTimeout> | undefined
  let flight: Promise<boolean> | null = null

  function cancelScheduled(): void {
    if (timer !== undefined) clearTimeout(timer)
    timer = undefined
  }

  function readRecovery(): string | null {
    if (disposed) return null
    try { return window.localStorage.getItem(options.recovery.key()) }
    catch { options.onRecoveryError('read'); return null }
  }

  function removeRecovery(key: string, expected: string): void {
    try {
      // A later edit or another tab owns any value other than the acknowledged snapshot.
      if (window.localStorage.getItem(key) === expected) window.localStorage.removeItem(key)
    } catch { options.onRecoveryError('remove') }
  }

  function schedule(delay: number): void {
    cancelScheduled()
    if (!disposed && revision !== confirmedRevision) {
      timer = setTimeout(() => { timer = undefined; void flush() }, delay)
    }
  }

  function reset(hasRecoveryChanges = false): void {
    if (disposed) return
    cancelScheduled()
    generation += 1
    revision += 1
    confirmedRevision = hasRecoveryChanges ? revision - 1 : revision
  }

  function changed(delay: number | null): void {
    if (disposed) return
    revision += 1
    const snapshot = options.snapshot()
    if (snapshot !== null) {
      try { window.localStorage.setItem(options.recovery.key(), options.recovery.serialize(snapshot)) }
      catch { options.onRecoveryError('write') }
    }
    options.onState('waiting')
    cancelScheduled()
    if (delay !== null) schedule(delay)
  }

  function flush(): Promise<boolean> {
    cancelScheduled()
    if (disposed) return Promise.resolve(false)
    if (flight) return flight
    const requestedIdentity = options.identity()
    const requestedGeneration = generation
    const isCurrent = () => !disposed && generation === requestedGeneration && options.identity() === requestedIdentity
    flight = drain(isCurrent).finally(() => {
      flight = null
      if (!disposed && !isCurrent() && revision !== confirmedRevision) schedule(0)
    })
    return flight
  }

  async function drain(isCurrent: () => boolean): Promise<boolean> {
    while (revision !== confirmedRevision) {
      if (!isCurrent() || options.canSave?.() === false) return false
      const snapshot = options.snapshot()
      if (snapshot === null) return false
      const capturedRevision = revision
      const key = options.recovery.key()
      const backup = options.recovery.serialize(snapshot)
      const context: DraftSaveContext = { isCurrent, isLatest: () => isCurrent() && revision === capturedRevision }
      options.onState('saving')
      try {
        const saved = await options.save(snapshot)
        if (!isCurrent()) return false
        await options.accept(saved, snapshot, context)
        if (!isCurrent()) return false
        confirmedRevision = capturedRevision
        if (context.isLatest()) {
          removeRecovery(key, backup)
          options.onState('saved')
        }
      } catch (reason) {
        if (isCurrent()) {
          cancelScheduled()
          options.onState('error')
          options.onError(reason)
        }
        return false
      }
    }
    return isCurrent()
  }

  return {
    get revision() { return revision },
    get dirty() { return revision !== confirmedRevision },
    get saving() { return flight !== null },
    reset,
    readRecovery,
    changed,
    schedule,
    flush,
    discardRecovery(expected: string): void {
      if (!disposed) removeRecovery(options.recovery.key(), expected)
    },
    dispose(): void { disposed = true; cancelScheduled() },
  }
}

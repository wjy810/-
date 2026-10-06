import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createDraftSaveQueue, type DraftSaveContext } from './draftSaveQueue'

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason: unknown) => void
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}

type Snapshot = { identity: string; value: string; version: number }

function harness(format: 'design' | 'answer' = 'answer') {
  const draft = { identity: 'draft-a', value: 'original', version: 1, online: true }
  const serialize = (snapshot: Snapshot) => format === 'design' ? JSON.stringify({ settings: snapshot.value }) : snapshot.value
  const save = vi.fn<(snapshot: Snapshot) => Promise<Snapshot>>()
  const accept = vi.fn(async (saved: Snapshot, _snapshot: Snapshot, context: DraftSaveContext) => {
    draft.version = saved.version
    if (context.isLatest()) draft.value = saved.value
  })
  const onState = vi.fn()
  const onError = vi.fn()
  const onRecoveryError = vi.fn()
  const queue = createDraftSaveQueue({
    identity: () => draft.identity,
    snapshot: () => ({ identity: draft.identity, value: draft.value, version: draft.version }),
    recovery: { key: () => draft.identity, serialize },
    canSave: () => draft.online,
    save, accept, onState, onError, onRecoveryError,
  })
  queue.reset()
  return { queue, draft, save, accept, onState, onError, onRecoveryError, serialize }
}

describe('shared draft save queue', () => {
  beforeEach(() => { vi.useFakeTimers(); window.localStorage.clear() })
  afterEach(() => { vi.useRealTimers(); vi.restoreAllMocks() })

  it.each(['design', 'answer'] as const)('serializes %s revisions and drains the latest edit with the acknowledged server version', async (format) => {
    const { queue, draft, save, serialize, onState } = harness(format)
    const first = deferred<Snapshot>()
    const second = deferred<Snapshot>()
    save.mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise)
    draft.value = 'first'
    queue.changed(500)
    await vi.advanceTimersByTimeAsync(500)
    const draining = queue.flush()
    draft.value = 'latest'
    queue.changed(500)
    await vi.advanceTimersByTimeAsync(500)
    expect(queue.flush()).toBe(draining)
    expect(save).toHaveBeenCalledTimes(1)
    first.resolve({ identity: 'draft-a', value: 'first normalized', version: 2 })
    await vi.advanceTimersByTimeAsync(0)
    expect(draft.value).toBe('latest')
    expect(onState).not.toHaveBeenCalledWith('saved')
    expect(window.localStorage.getItem(draft.identity)).toBe(serialize({ ...draft }))
    expect(save).toHaveBeenLastCalledWith({ identity: 'draft-a', value: 'latest', version: 2 })
    second.resolve({ identity: 'draft-a', value: 'latest normalized', version: 3 })
    expect(await draining).toBe(true)
    expect(draft.value).toBe('latest normalized')
    expect(queue.dirty).toBe(false)
    expect(window.localStorage.getItem(draft.identity)).toBeNull()
    expect(onState).toHaveBeenLastCalledWith('saved')
    queue.dispose()
  })

  it('flushes the debounce immediately and waits for asynchronous acceptance before reporting success', async () => {
    const { queue, draft, save, accept, onState } = harness()
    const refreshedLayout = deferred<void>()
    save.mockImplementation(async (snapshot) => ({ ...snapshot, version: 2 }))
    accept.mockImplementation(() => refreshedLayout.promise)
    draft.value = 'edited'
    queue.changed(850)
    const completed = vi.fn()
    const draining = queue.flush().then(completed)
    await vi.advanceTimersByTimeAsync(0)
    expect(save).toHaveBeenCalledOnce()
    expect(completed).not.toHaveBeenCalled()
    expect(onState).not.toHaveBeenCalledWith('saved')
    refreshedLayout.resolve()
    await draining
    expect(completed).toHaveBeenCalledWith(true)
    await vi.advanceTimersByTimeAsync(850)
    expect(save).toHaveBeenCalledOnce()
    queue.dispose()
  })

  it.each(['design', 'answer'] as const)('retains %s recovery and dirty state after a conflict without automatic retry', async (format) => {
    const { queue, draft, save, serialize, onError } = harness(format)
    const conflict = Object.assign(new Error('version conflict'), { status: 409 })
    const pending = deferred<Snapshot>()
    save.mockReturnValue(pending.promise)
    draft.value = 'in-flight edit'
    queue.changed(null)
    const draining = queue.flush()
    draft.value = 'unsaved latest'
    queue.changed(500)
    pending.reject(conflict)
    expect(await draining).toBe(false)
    await vi.advanceTimersByTimeAsync(5000)
    expect(save).toHaveBeenCalledOnce()
    expect(queue.dirty).toBe(true)
    expect(queue.readRecovery()).toBe(serialize({ ...draft }))
    expect(onError).toHaveBeenCalledWith(conflict)
    queue.dispose()
  })

  it('ignores a previous identity response and schedules the recovered current identity after it finishes', async () => {
    const { queue, draft, save, accept } = harness()
    const old = deferred<Snapshot>()
    save.mockReturnValueOnce(old.promise).mockImplementationOnce(async (snapshot) => ({ ...snapshot, version: 5 }))
    draft.value = 'old answer'
    queue.changed(null)
    const oldFlush = queue.flush()
    draft.identity = 'draft-b'
    draft.value = 'recovered next answer'
    draft.version = 4
    window.localStorage.setItem('draft-b', draft.value)
    queue.reset(true)
    old.resolve({ identity: 'draft-a', value: 'old server answer', version: 2 })
    expect(await oldFlush).toBe(false)
    await vi.advanceTimersByTimeAsync(0)
    expect(accept).toHaveBeenCalledTimes(1)
    expect(save).toHaveBeenLastCalledWith({ identity: 'draft-b', value: 'recovered next answer', version: 4 })
    expect(draft.version).toBe(5)
    expect(window.localStorage.getItem('draft-a')).toBe('old answer')
    expect(window.localStorage.getItem('draft-b')).toBeNull()
    queue.dispose()
  })

  it('invalidates a response when the same identity is rehydrated while its request is running', async () => {
    const { queue, draft, save, accept } = harness()
    const old = deferred<Snapshot>()
    save.mockReturnValue(old.promise)
    draft.value = 'old edit'
    queue.changed(null)
    const draining = queue.flush()
    draft.value = 'newly loaded'
    draft.version = 6
    queue.reset()
    old.resolve({ identity: 'draft-a', value: 'late old edit', version: 2 })
    expect(await draining).toBe(false)
    expect(accept).not.toHaveBeenCalled()
    expect(draft.value).toBe('newly loaded')
    expect(draft.version).toBe(6)
    queue.dispose()
  })

  it('does not accept late results or clear another mounted page recovery after disposal', async () => {
    const { queue, draft, save, accept, onState } = harness()
    const old = deferred<Snapshot>()
    save.mockReturnValue(old.promise)
    draft.value = 'old edit'
    queue.changed(null)
    const draining = queue.flush()
    queue.dispose()
    window.localStorage.setItem('draft-a', 'another page edit')
    onState.mockClear()
    old.resolve({ identity: 'draft-a', value: 'old edit', version: 2 })
    expect(await draining).toBe(false)
    expect(accept).not.toHaveBeenCalled()
    expect(onState).not.toHaveBeenCalled()
    expect(window.localStorage.getItem('draft-a')).toBe('another page edit')
  })

  it('cancels scheduled work on disposal and makes explicit flush fail without calling the adapter', async () => {
    const { queue, save } = harness()
    queue.changed(500)
    queue.dispose()
    await vi.advanceTimersByTimeAsync(1000)
    expect(await queue.flush()).toBe(false)
    expect(save).not.toHaveBeenCalled()
  })

  it('lets asynchronous acceptance guard its follow-up updates after the page is disposed', async () => {
    const { queue, save, accept, onState } = harness()
    const pendingLayout = deferred<void>()
    const applyLayout = vi.fn()
    save.mockImplementation(async (snapshot) => ({ ...snapshot, version: 2 }))
    accept.mockImplementation(async (_saved, _snapshot, context) => {
      await pendingLayout.promise
      if (context.isCurrent()) applyLayout()
    })
    queue.changed(null)
    const draining = queue.flush()
    await vi.advanceTimersByTimeAsync(0)
    expect(accept).toHaveBeenCalledOnce()
    queue.dispose()
    onState.mockClear()
    pendingLayout.resolve()
    expect(await draining).toBe(false)
    expect(applyLayout).not.toHaveBeenCalled()
    expect(onState).not.toHaveBeenCalled()
    expect(window.localStorage.getItem('draft-a')).toBe('original')
  })

  it('preserves a newer backup written by another tab even when this local revision was acknowledged', async () => {
    const { queue, draft, save } = harness()
    const pending = deferred<Snapshot>()
    save.mockReturnValue(pending.promise)
    draft.value = 'this tab edit'
    queue.changed(null)
    const draining = queue.flush()
    window.localStorage.setItem('draft-a', 'another tab edit')
    pending.resolve({ identity: 'draft-a', value: draft.value, version: 2 })
    expect(await draining).toBe(true)
    expect(window.localStorage.getItem('draft-a')).toBe('another tab edit')
    queue.dispose()
  })

  it('keeps offline edits recoverable and drains them after connectivity returns', async () => {
    const { queue, draft, save } = harness()
    save.mockImplementation(async (snapshot) => ({ ...snapshot, version: 2 }))
    draft.online = false
    draft.value = 'offline edit'
    queue.changed(null)
    expect(await queue.flush()).toBe(false)
    expect(save).not.toHaveBeenCalled()
    expect(queue.readRecovery()).toBe('offline edit')
    draft.online = true
    expect(await queue.flush()).toBe(true)
    expect(save).toHaveBeenCalledOnce()
    queue.dispose()
  })

  it('reports unavailable recovery storage without preventing a confirmed server save', async () => {
    const { queue, save, onRecoveryError, onError } = harness()
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => { throw new Error('storage blocked') })
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('storage blocked') })
    save.mockImplementation(async (snapshot) => ({ ...snapshot, version: 2 }))
    expect(queue.readRecovery()).toBeNull()
    queue.changed(null)
    expect(await queue.flush()).toBe(true)
    expect(onRecoveryError.mock.calls).toEqual([['read'], ['write'], ['remove']])
    expect(onError).not.toHaveBeenCalled()
    expect(queue.dirty).toBe(false)
    queue.dispose()
  })
})

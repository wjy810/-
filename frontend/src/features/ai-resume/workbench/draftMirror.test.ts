import assert from 'node:assert/strict'
import test from 'node:test'
import {
  draftMirrorKey,
  readDraftMirror,
  reconcileDraftMirror,
  samePayload,
  writeDraftMirror,
} from './draftMirror.ts'

function memoryStorage(): Storage {
  const data = new Map<string, string>()
  return {
    get length() { return data.size },
    key: (index: number) => [...data.keys()][index] ?? null,
    getItem: (key: string) => data.get(key) ?? null,
    setItem: (key: string, value: string) => { data.set(key, value) },
    removeItem: (key: string) => { data.delete(key) },
    clear: () => data.clear(),
  }
}

const NOW = Date.parse('2026-10-18T08:00:00Z')
const draft = (payload: Record<string, unknown>, baseVersion = 3, savedAt = '2026-10-18T07:50:00Z') => ({ payload, baseVersion, savedAt })

test('unsent edits typed against the current version are picked up again', () => {
  assert.equal(reconcileDraftMirror(draft({ text: '新的简介' }), 3, { text: '旧的简介' }, NOW), 'resume')
})

test('edits typed against an older version are a conflict, never a silent overwrite', () => {
  assert.equal(reconcileDraftMirror(draft({ text: '我的修改' }, 2), 3, { text: '别处的修改' }, NOW), 'conflict')
})

test('a mirror equal to the server copy, or older than two weeks, is dropped', () => {
  assert.equal(reconcileDraftMirror(draft({ text: '一样' }, 2), 3, { text: '一样' }, NOW), 'drop')
  assert.equal(reconcileDraftMirror(draft({ text: '很久以前' }, 3, '2026-09-01T00:00:00Z'), 3, { text: '现在' }, NOW), 'drop')
  assert.equal(reconcileDraftMirror(draft({ text: '无时间' }, 3, ''), 3, { text: '现在' }, NOW), 'drop')
})

test('key order and blank fields do not count as changes', () => {
  assert.equal(samePayload({ items: [{ school: 'A', major: '' }], note: null }, { items: [{ school: 'A' }] }), true)
  assert.equal(samePayload({ items: [{ school: '', major: '' }] }, {}), true)
  assert.equal(samePayload({ a: 1, b: 2 }, { b: 2, a: 1 }), true)
  assert.equal(samePayload({ items: [{ school: 'A' }, { school: 'B' }] }, { items: [{ school: 'B' }, { school: 'A' }] }), false)
})

test('broken or foreign entries read as no mirror', () => {
  const storage = memoryStorage()
  const key = draftMirrorKey('conv', 'branch', 'card')
  storage.setItem(key, '{not json')
  assert.equal(readDraftMirror(storage, key), null)
  storage.setItem(key, JSON.stringify({ payload: 'x', baseVersion: 1 }))
  assert.equal(readDraftMirror(storage, key), null)
  writeDraftMirror(storage, key, draft({ text: '草稿' }))
  assert.deepEqual(readDraftMirror(storage, key)?.payload, { text: '草稿' })
  assert.equal(readDraftMirror(null, key), null)
})

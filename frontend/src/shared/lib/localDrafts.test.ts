import assert from 'node:assert/strict'
import test from 'node:test'
import { clearLocalDrafts } from './localDrafts.ts'

test('signing out clears every local draft and nothing else', () => {
  const data = new Map<string, string>([
    ['jp:card-draft:c:b:card', '{}'],
    ['jp:card-draft:c:b:card:conflict', '{}'],
    ['jobproof:ai-resume:design:m:t', '{}'],
    ['jobproof:mock-interview:s:q', '答案'],
    ['jp:preferences', '{}'],
    ['jp:resumes:view', 'list'],
  ])
  const storage = {
    get length() { return data.size },
    key: (index: number) => [...data.keys()][index] ?? null,
    getItem: (key: string) => data.get(key) ?? null,
    setItem: (key: string, value: string) => { data.set(key, value) },
    removeItem: (key: string) => { data.delete(key) },
    clear: () => data.clear(),
  } as Storage
  clearLocalDrafts(storage)
  assert.deepEqual([...data.keys()], ['jp:preferences', 'jp:resumes:view'])
  clearLocalDrafts(null)
})

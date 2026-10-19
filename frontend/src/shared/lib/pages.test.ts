import assert from 'node:assert/strict'
import test from 'node:test'
import { collectPages } from './pages.ts'

function source(total: number) {
  const calls: number[] = []
  const load = async (page: number, size: number) => {
    calls.push(page)
    const start = page * size
    return { items: Array.from({ length: Math.max(0, Math.min(size, total - start)) }, (_, index) => start + index), total }
  }
  return { load, calls }
}

test('reads every page until all items are in', async () => {
  const { load, calls } = source(230)
  const result = await collectPages(load)
  assert.equal(result.items.length, 230)
  assert.equal(result.truncated, false)
  assert.deepEqual(calls, [0, 1, 2])
})

test('stops at the bound and reports what was left out', async () => {
  const { load, calls } = source(1200)
  const result = await collectPages(load, 250)
  assert.equal(result.items.length, 250)
  assert.equal(result.total, 1200)
  assert.equal(result.truncated, true)
  assert.deepEqual(calls, [0, 1, 2])
})

test('one short page is enough for a small library', async () => {
  const { load, calls } = source(7)
  assert.deepEqual((await collectPages(load)).items.length, 7)
  assert.deepEqual(calls, [0])
})

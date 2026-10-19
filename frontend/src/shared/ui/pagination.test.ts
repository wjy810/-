import assert from 'node:assert/strict'
import test from 'node:test'
import { pageCountOf, pageSlots } from './pagination.ts'

test('short lists show every page', () => {
  assert.deepEqual(pageSlots(0, 1), [0])
  assert.deepEqual(pageSlots(3, 7), [0, 1, 2, 3, 4, 5, 6])
  assert.deepEqual(pageSlots(0, 0), [])
})

test('long lists keep the first, the last and the neighbours of the current page', () => {
  assert.deepEqual(pageSlots(0, 20), [0, 1, 2, 3, 4, 'gap-end', 19])
  assert.deepEqual(pageSlots(3, 20), [0, 1, 2, 3, 4, 'gap-end', 19])
  assert.deepEqual(pageSlots(4, 20), [0, 'gap-start', 3, 4, 5, 'gap-end', 19])
  assert.deepEqual(pageSlots(15, 20), [0, 'gap-start', 14, 15, 16, 'gap-end', 19])
  assert.deepEqual(pageSlots(16, 20), [0, 'gap-start', 15, 16, 17, 18, 19])
  assert.deepEqual(pageSlots(19, 20), [0, 'gap-start', 15, 16, 17, 18, 19])
})

test('windowed lists keep a constant length and clamp an out-of-range page', () => {
  for (let page = -2; page < 30; page += 1) assert.equal(pageSlots(page, 25).length, 7)
  assert.deepEqual(pageSlots(99, 10), [0, 'gap-start', 5, 6, 7, 8, 9])
})

test('page count never drops below one', () => {
  assert.equal(pageCountOf(0, 20), 1)
  assert.equal(pageCountOf(20, 20), 1)
  assert.equal(pageCountOf(21, 20), 2)
  assert.equal(pageCountOf(5, 0), 5)
})

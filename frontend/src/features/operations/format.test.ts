import assert from 'node:assert/strict'
import test from 'node:test'
import { compact, niceMax, shortDay, sum } from './format.ts'

test('numbers stay exact below ten thousand and compact above', () => {
  assert.equal(compact(1284), '1,284')
  assert.equal(compact(12_900), '1.3万')
  assert.equal(compact(1_234_567), '123万')
})

test('the axis top is a clean number at or above the data', () => {
  assert.equal(niceMax([0, 0]), 4)
  assert.equal(niceMax([3]), 4)
  assert.equal(niceMax([7]), 10)
  assert.equal(niceMax([13]), 20)
  assert.equal(niceMax([48]), 50)
  assert.equal(niceMax([101]), 200)
  assert.equal(shortDay('2026-10-19'), '10-19')
  assert.equal(sum([1, 2, 3]), 6)
})

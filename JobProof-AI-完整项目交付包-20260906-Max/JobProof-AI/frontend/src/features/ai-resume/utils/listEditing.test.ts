import assert from 'node:assert/strict'
import test from 'node:test'
import { moveListItem } from './listEditing.ts'

test('moves a repeated resume record without mutating the source array', () => {
  const source = ['教育 A', '教育 B', '教育 C']
  const moved = moveListItem(source, 2, -1)

  assert.deepEqual(moved, ['教育 A', '教育 C', '教育 B'])
  assert.deepEqual(source, ['教育 A', '教育 B', '教育 C'])
})

test('keeps the order unchanged when a move would cross a boundary', () => {
  assert.deepEqual(moveListItem(['A', 'B'], 0, -1), ['A', 'B'])
  assert.deepEqual(moveListItem(['A', 'B'], 1, 1), ['A', 'B'])
})

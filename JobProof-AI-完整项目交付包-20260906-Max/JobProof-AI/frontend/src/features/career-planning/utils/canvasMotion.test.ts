import assert from 'node:assert/strict'
import { test } from 'node:test'
import { branchGrowth, confirmedMastery } from './canvasMotion.ts'

test('growth follows the existing right-root hierarchy and ends in less than 600ms', () => {
  const relations = Array.from({ length: 10 }, (_, i) => ({ id: `e${i}`, type: 'TREE_PARENT', fromNodeId: `n${i + 1}`, toNodeId: `n${i}` }))
  const motion = branchGrowth(new Set(['n0']), new Set(Array.from({ length: 11 }, (_, i) => `n${i}`)), relations)
  assert.equal(motion.nodes.get('n1'), 0)
  assert.equal(motion.nodes.get('n10'), 280)
  assert.equal(motion.edges.get('e9'), 280)
  assert.ok(Math.max(...motion.nodes.values()) + 280 <= 600)
})
test('collapse or unchanged visibility creates no spurious growth', () => {
  assert.equal(branchGrowth(new Set(['a', 'b']), new Set(['a']), []).nodes.size, 0)
  assert.equal(branchGrowth(new Set(['a']), new Set(['a']), []).nodes.size, 0)
})
test('completion feedback needs an existing node to actually transition to mastered', () => {
  assert.deepEqual([...confirmedMastery([{ logicalNodeId: 'a', status: 'PENDING_VALIDATION' }], [{ logicalNodeId: 'a', status: 'MASTERED' }, { logicalNodeId: 'new', status: 'MASTERED' }])], ['a'])
  assert.equal(confirmedMastery([{ logicalNodeId: 'a', status: 'MASTERED' }], [{ logicalNodeId: 'a', status: 'MASTERED' }]).size, 0)
  assert.equal(confirmedMastery([{ logicalNodeId: 'a', status: 'LEARNING' }], [{ logicalNodeId: 'a', status: 'PENDING_VALIDATION' }]).size, 0)
})

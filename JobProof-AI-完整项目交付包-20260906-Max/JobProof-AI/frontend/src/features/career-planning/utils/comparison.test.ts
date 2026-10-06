import assert from 'node:assert/strict'
import test from 'node:test'
import type { CareerRecommendation } from '../types'
import { selectedCareerComparisons, toggleCareerComparison } from './comparison.ts'

test('career comparison keeps selection order and enforces the three item limit', () => {
  let ids: string[] = []
  for (const id of ['a', 'b', 'c']) ids = toggleCareerComparison(ids, id).ids
  const blocked = toggleCareerComparison(ids, 'd')

  assert.deepEqual(blocked.ids, ['a', 'b', 'c'])
  assert.equal(blocked.limitReached, true)
  assert.deepEqual(toggleCareerComparison(ids, 'b').ids, ['a', 'c'])
})

test('career comparison resolves only recommendations still present in the active set', () => {
  const recommendation = (id: string): CareerRecommendation => ({
    id, taxonomyNodeId: id, title: id, tier: 'READY_NOW', fitSummary: '',
    rationale: [], gaps: [], sourceRefs: [], favorite: false, sortOrder: 0,
  })

  assert.deepEqual(selectedCareerComparisons([recommendation('a'), recommendation('c')], ['c', 'missing', 'a']).map(item => item.id), ['c', 'a'])
})

import assert from 'node:assert/strict'
import test from 'node:test'
import { parseUpdateFilters, publishedFromForRange, serializeUpdateFilters } from './filters.ts'
import { moduleLabel } from './labels.ts'

test('update filters restore valid URL state and reject invalid pages', () => {
  assert.deepEqual(parseUpdateFilters({
    q: '  PDF 导出  ', type: 'FEATURE', module: 'AI_RESUME', versionFrom: 'v1.2.0',
    versionTo: 'v2.0.0', range: '30', page: '2',
  }), {
    q: 'PDF 导出', type: 'FEATURE', module: 'AI_RESUME', versionFrom: 'v1.2.0',
    versionTo: 'v2.0.0', range: '30', page: 2,
  })
  assert.equal(parseUpdateFilters({ page: '-4' }).page, 0)
  assert.equal(parseUpdateFilters({ page: 'not-a-number' }).page, 0)
})

test('update filters serialize compactly and preserve pagination', () => {
  assert.deepEqual(serializeUpdateFilters({
    q: '  安全更新 ', type: '', module: 'ACCOUNT', versionFrom: '', versionTo: '', range: '7', page: 3,
  }), {
    q: '安全更新', type: undefined, module: 'ACCOUNT', versionFrom: undefined,
    versionTo: undefined, range: '7', page: '3',
  })
})

test('published range converts to a stable ISO lower bound', () => {
  assert.equal(publishedFromForRange('7', Date.parse('2026-08-26T00:00:00.000Z')), '2026-08-19T00:00:00.000Z')
  assert.equal(publishedFromForRange('', 0), undefined)
  assert.equal(publishedFromForRange('invalid', 0), undefined)
})

test('recent product modules use current public labels', () => {
  assert.equal(moduleLabel('JOB_MATCHING'), '岗位匹配')
  assert.equal(moduleLabel('MOCK_INTERVIEW'), '模拟面试')
  assert.equal(moduleLabel('CAREER_PLANNING'), '职业规划')
})

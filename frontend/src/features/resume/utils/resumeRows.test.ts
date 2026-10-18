import assert from 'node:assert/strict'
import test from 'node:test'
import { buildRows, rowAction, rowLink } from './resumeRows.ts'

const master = { id: 'm1', title: '  数据分析简历 ', status: 'DRAFT', version: 3, updatedAt: '2026-10-01T00:00:00Z' }

test('a resume and its versions become rows, numbered by creation order', () => {
  const { rows, detailGap } = buildRows([master], [{
    ...master,
    keyOutcomes: [],
    versions: [
      { id: 'v2', masterId: 'm1', status: 'FROZEN', immutable: true, version: 1, createdAt: '2026-10-03T00:00:00Z', frozenAt: '2026-10-03T00:00:00Z', snapshot: { title: '快照 B' } },
      { id: 'v1', masterId: 'm1', status: 'FROZEN', immutable: true, version: 1, createdAt: '2026-10-02T00:00:00Z', frozenAt: '2026-10-02T00:00:00Z', snapshot: {} },
    ],
  } as never])
  assert.equal(detailGap, 0)
  assert.deepEqual(rows.map(row => [row.kind, row.title, row.ordinal]), [
    ['MASTER', '数据分析简历', null],
    ['VERSION', '快照 B', 2],
    ['VERSION', '数据分析简历 · 快照', 1],
  ])
  assert.equal(rows[1]!.rowType, '冻结版本')
  assert.equal(rowAction(rows[0]!), '编辑')
  assert.equal(rowLink(rows[1]!), '/resumes/m1?versionId=v2')
})

test('a resume whose details failed still has a row and is counted as a gap', () => {
  const { rows, detailGap } = buildRows([master], [null])
  assert.equal(detailGap, 1)
  assert.equal(rows.length, 1)
  assert.equal(rows[0]!.coverage, null)
})

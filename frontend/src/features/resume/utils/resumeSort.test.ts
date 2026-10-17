import assert from 'node:assert/strict'
import test from 'node:test'
import { groupUnderMasters, isResumeSort, sortResumes } from './resumeSort.ts'

const rows = [
  { title: '简历 10', updatedAt: '2026-10-02T00:00:00Z' },
  { title: '阿里巴巴-后端', updatedAt: '2026-10-05T00:00:00Z' },
  { title: '简历 2', updatedAt: '2026-10-01T00:00:00Z' },
  { title: 'Backend', updatedAt: null },
]

test('newest first by default, oldest first on request', () => {
  assert.deepEqual(sortResumes(rows, 'updated-desc').map(row => row.title), ['阿里巴巴-后端', '简历 10', '简历 2', 'Backend'])
  assert.deepEqual(sortResumes(rows, 'updated-asc').map(row => row.title), ['Backend', '简历 2', '简历 10', '阿里巴巴-后端'])
})

test('names sort by pinyin with numbers in numeric order, without touching the input', () => {
  const titles = sortResumes(rows, 'title').map(row => row.title)
  assert.ok(titles.indexOf('阿里巴巴-后端') < titles.indexOf('简历 2'))
  assert.ok(titles.indexOf('简历 2') < titles.indexOf('简历 10'))
  assert.equal(rows[0]!.title, '简历 10')
})

test('recognises stored sort values', () => {
  assert.equal(isResumeSort('title'), true)
  assert.equal(isResumeSort('size'), false)
})

test('versions sit under their own resume, newest first, whatever the resume order', () => {
  const items = [
    { kind: 'VERSION' as const, masterId: 'a', title: 'A v1', updatedAt: '2026-10-03T00:00:00Z' },
    { kind: 'MASTER' as const, masterId: 'a', title: 'Alpha', updatedAt: '2026-10-01T00:00:00Z' },
    { kind: 'VERSION' as const, masterId: 'a', title: 'A v2', updatedAt: '2026-10-04T00:00:00Z' },
    { kind: 'MASTER' as const, masterId: 'b', title: 'Beta', updatedAt: '2026-10-02T00:00:00Z' },
    { kind: 'VERSION' as const, masterId: 'gone', title: 'Orphan', updatedAt: '2026-10-05T00:00:00Z' },
  ]
  assert.deepEqual(groupUnderMasters(items, 'updated-desc').map(item => item.title), ['Beta', 'Alpha', 'A v2', 'A v1', 'Orphan'])
  assert.deepEqual(groupUnderMasters(items, 'title').map(item => item.title), ['Alpha', 'A v2', 'A v1', 'Beta', 'Orphan'])
})

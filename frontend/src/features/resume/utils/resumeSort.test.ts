import assert from 'node:assert/strict'
import test from 'node:test'
import { isResumeSort, sortResumes } from './resumeSort.ts'

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

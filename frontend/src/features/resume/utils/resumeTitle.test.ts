import assert from 'node:assert/strict'
import test from 'node:test'
import { RESUME_TITLE_MAX, normalizeResumeTitle, resumeTitleIssue, titleMatches } from './resumeTitle.ts'

test('resume names are trimmed and inner whitespace collapsed', () => {
  assert.equal(normalizeResumeTitle('  数据分析   实习 \n'), '数据分析 实习')
})

test('a resume name must be non-empty and short enough', () => {
  assert.equal(resumeTitleIssue('   '), '请输入简历名称')
  assert.equal(resumeTitleIssue('后端开发 · 校招'), '')
  assert.equal(resumeTitleIssue('名'.repeat(RESUME_TITLE_MAX)), '')
  assert.match(resumeTitleIssue('名'.repeat(RESUME_TITLE_MAX + 1)), /最多 60 个字/)
})

test('search matches names case-insensitively and an empty query matches everything', () => {
  assert.equal(titleMatches('Java 后端简历', 'java'), true)
  assert.equal(titleMatches('Java 后端简历', '  '), true)
  assert.equal(titleMatches('产品经理简历', '后端'), false)
})

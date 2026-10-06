import assert from 'node:assert/strict'
import test from 'node:test'
import { chooseSummaryDraft, distinctSummaryCandidates, summaryVisibleCharacters } from './summarySuggestions.ts'

test('choosing an AI summary returns a draft without mutating the existing text', () => {
  const existing = '原有个人简介内容'
  const choice = chooseSummaryDraft(existing, '  新的 AI 个人简介候选  ')
  assert.equal(existing, '原有个人简介内容')
  assert.deepEqual(choice, {
    previous: '原有个人简介内容',
    next: '新的 AI 个人简介候选',
    changed: true,
  })
})

test('summary helpers count visible characters and reject duplicate options', () => {
  assert.equal(summaryVisibleCharacters('Java 后端\n开发'), 8)
  assert.equal(distinctSummaryCandidates([{ text: '版本 A' }, { text: '版本B' }, { text: '版本 A ' }]), false)
  assert.equal(distinctSummaryCandidates([{ text: '专业简洁' }, { text: '成果导向' }, { text: '稳健正式' }]), true)
})

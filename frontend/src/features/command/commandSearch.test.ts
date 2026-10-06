import assert from 'node:assert/strict'
import test from 'node:test'
import { rankCommands, scoreCommand } from './commandSearch.ts'

const resume = { id: 'nav:resumes', title: '简历', pinyin: 'jianli', initials: 'jl' }
const match = { id: 'nav:job-match', title: '岗位匹配', pinyin: 'gangweipipei', initials: 'gwpp', keywords: ['JD', 'match'] }

test('matches titles, pinyin initials, full pinyin and keywords', () => {
  assert.equal(scoreCommand(resume, '简历'), 0)
  assert.equal(scoreCommand(resume, 'jl'), 3)
  assert.equal(scoreCommand(resume, 'jian'), 4)
  assert.equal(scoreCommand(match, 'jd'), 7)
  assert.equal(scoreCommand(match, 'xyz'), null)
})

test('ranks better matches first and surfaces recent commands for an empty query', () => {
  assert.deepEqual(rankCommands([match, resume], 'jl').map(c => c.id), ['nav:resumes'])
  assert.deepEqual(rankCommands([resume, match], '', ['nav:job-match']).map(c => c.id), ['nav:job-match', 'nav:resumes'])
})

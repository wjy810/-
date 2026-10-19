import assert from 'node:assert/strict'
import test from 'node:test'
import { interviewPrep } from './prep.ts'

const base = { company: '合成科技', positionTitle: 'Java 后端工程师', jobMatchId: null }

test('a mock interview is offered once the company moves to a test or an interview', () => {
  assert.equal(interviewPrep({ ...base, stage: 'APPLIED' }), null)
  assert.equal(interviewPrep({ ...base, stage: 'CLOSED' }), null)
  assert.deepEqual(interviewPrep({ ...base, stage: 'INTERVIEW' })?.query, { company: '合成科技', position: 'Java 后端工程师' })
  const matched = interviewPrep({ ...base, stage: 'ASSESSMENT', jobMatchId: 'm1' })
  assert.deepEqual(matched?.query, { jobMatchId: 'm1' })
  assert.match(matched?.detail ?? '', /岗位匹配报告/)
})

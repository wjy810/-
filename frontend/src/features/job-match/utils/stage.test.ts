import assert from 'node:assert/strict'
import test from 'node:test'
import { canReach, latestRunNotice, serverStage, statusLabel, statusTone, stepNumber, summaryRoute } from './stage.ts'

test('server status resolves to the last durable workflow stage', () => {
  assert.equal(serverStage({ status: 'DRAFT', checkpoint: 'PARSE_JD' }), 'jd')
  assert.equal(serverStage({ status: 'JD_PARSED', checkpoint: 'JD_PARSED' }), 'jd')
  assert.equal(serverStage({ status: 'JD_PARSED', checkpoint: 'JD_CONFIRMED' }), 'resume')
  assert.equal(serverStage({ status: 'RESUME_CONFIRMED', checkpoint: 'RESUME_CONFIRMED' }), 'evidence')
  assert.equal(serverStage({ status: 'EVIDENCE_AUTHORIZED', checkpoint: 'EVIDENCE_AUTHORIZED' }), 'confirm')
  assert.equal(serverStage({ status: 'ANALYSIS_PAUSED', checkpoint: 'ANALYSIS_PAUSED' }), 'analyzing')
  assert.equal(serverStage({ status: 'NEEDS_CLARIFICATION', checkpoint: 'CLARIFICATION_REQUIRED' }), 'clarifications')
  assert.equal(serverStage({ status: 'COMPLETED', checkpoint: 'REPORT_READY' }), 'report')
})

test('stage reachability never lets an early workflow open later data', () => {
  assert.equal(canReach('DRAFT', 'resume'), false)
  assert.equal(canReach('JD_PARSED', 'resume'), true)
  assert.equal(canReach('RESUME_CONFIRMED', 'confirm'), false)
  assert.equal(canReach('EVIDENCE_AUTHORIZED', 'confirm'), true)
  assert.equal(canReach('NEEDS_CLARIFICATION', 'report'), false)
  assert.equal(canReach('COMPLETED', 'report'), true)
  assert.equal(stepNumber('clarifications'), 4)
})

test('status copy and tones distinguish success, recovery and failure', () => {
  assert.equal(statusLabel('JD_PARSE_FAILED'), 'JD 解析失败')
  assert.equal(statusLabel('NEEDS_CLARIFICATION'), '需要确认')
  assert.equal(statusTone('COMPLETED'), 'success')
  assert.equal(statusTone('ANALYSIS_PAUSED'), 'danger')
  assert.equal(statusTone('CANCELLED'), 'neutral')
})

test('a paused or cancelled re-analysis opens the last finished report; a first run stays on its progress page', () => {
  assert.deepEqual(summaryRoute({ id: 'm1', status: 'ANALYSIS_PAUSED', reportVersions: 2 }), { name: 'job-match-report', params: { id: 'm1' } })
  assert.deepEqual(summaryRoute({ id: 'm1', status: 'CANCELLED', reportVersions: 1 }), { name: 'job-match-report', params: { id: 'm1' } })
  assert.deepEqual(summaryRoute({ id: 'm1', status: 'ANALYSIS_PAUSED', reportVersions: 0 }), { name: 'job-match-analyzing', params: { id: 'm1' } })
  assert.deepEqual(summaryRoute({ id: 'm1', status: 'ANALYZING', reportVersions: 3 }), { name: 'job-match-analyzing', params: { id: 'm1' } })
})

test('the report says what happened to the latest run and what can be done next', () => {
  assert.equal(latestRunNotice('COMPLETED', '', false), null)
  assert.deepEqual(latestRunNotice('ANALYSIS_PAUSED', 'AI 服务响应超时。', false)?.actions, ['RETRY', 'CANCEL'])
  assert.match(latestRunNotice('ANALYSIS_PAUSED', 'AI 服务响应超时。', false)!.text, /响应超时。下面仍是上一版报告/)
  assert.match(latestRunNotice('ANALYSIS_PAUSED', 'x', true)!.text, /规则预估/)
  assert.deepEqual(latestRunNotice('ANALYZING', '', false)?.actions, ['PROGRESS'])
  assert.deepEqual(latestRunNotice('NEEDS_CLARIFICATION', '', false)?.actions, ['CLARIFY'])
  assert.deepEqual(latestRunNotice('CANCELLED', '', false)?.actions, [])
})

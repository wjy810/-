import assert from 'node:assert/strict'
import test from 'node:test'
import { canReach, serverStage, statusLabel, statusTone, stepNumber } from './stage.ts'

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
  assert.equal(statusTone('COMPLETED'), 'green')
  assert.equal(statusTone('ANALYSIS_PAUSED'), 'red')
  assert.equal(statusTone('CANCELLED'), 'gray')
})

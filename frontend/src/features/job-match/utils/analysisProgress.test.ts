import assert from 'node:assert/strict'
import test from 'node:test'
import {
  ANALYSIS_STAGES,
  analysisStageIndex,
  analysisStageState,
  analysisStatusText,
  pauseReasonText,
  serverProgressPercent,
} from './analysisProgress.ts'

function states(checkpoint: string, status: Parameters<typeof analysisStageState>[2]) {
  const current = analysisStageIndex(checkpoint)
  return ANALYSIS_STAGES.map((_, index) => analysisStageState(index, current, status))
}

test('stage states follow the checkpoint the server reported', () => {
  assert.deepEqual(states('FREEZE_INPUTS', 'ANALYZING'), ['active', 'pending', 'pending', 'pending', 'pending'])
  assert.deepEqual(states('AI_SEMANTIC_ANALYSIS', 'ANALYZING'), ['done', 'done', 'active', 'pending', 'pending'])
  assert.deepEqual(states('RULE_REPORT_READY', 'ANALYZING'), ['done', 'done', 'active', 'pending', 'pending'])
  assert.deepEqual(states('FACT_VALIDATION', 'ANALYZING'), ['done', 'done', 'done', 'active', 'pending'])
  assert.deepEqual(states('REPORT_READY', 'COMPLETED'), ['done', 'done', 'done', 'done', 'done'])
})

test('an unknown checkpoint claims no finished stage', () => {
  assert.equal(analysisStageIndex('SOMETHING_NEW'), -1)
  assert.deepEqual(states('SOMETHING_NEW', 'ANALYZING'), ['pending', 'pending', 'pending', 'pending', 'pending'])
  assert.equal(analysisStatusText('ANALYZING', 'SOMETHING_NEW'), '分析进行中')
})

test('a resumed run waits at the first stage instead of guessing where it stopped', () => {
  assert.deepEqual(states('ANALYSIS_PAUSED', 'ANALYZING'), ['active', 'pending', 'pending', 'pending', 'pending'])
  assert.equal(analysisStatusText('ANALYZING', 'ANALYSIS_PAUSED'), '已提交恢复，等待后台开始')
})

test('only a positive server percentage is shown', () => {
  assert.equal(serverProgressPercent(72), 72)
  assert.equal(serverProgressPercent(88.6), 89)
  assert.equal(serverProgressPercent(140), 100)
  assert.equal(serverProgressPercent(0), null)
  assert.equal(serverProgressPercent(undefined), null)
  assert.equal(serverProgressPercent(Number.NaN), null)
})

test('pause reasons are plain language, not error codes', () => {
  assert.equal(pauseReasonText('JOB_MATCH_AI_SCHEMA_INVALID'), 'AI 返回的内容没有通过结构与事实来源校验。')
  assert.equal(pauseReasonText('JOB_MATCH_AI_FAILED'), 'AI 服务这次没有完成分析。')
  assert.equal(pauseReasonText(null), 'AI 服务这次没有完成分析。')
  assert.doesNotMatch(pauseReasonText('JOB_MATCH_AI_FAILED'), /[A-Z_]{4,}/)
})

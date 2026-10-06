import assert from 'node:assert/strict'
import test from 'node:test'
import {
  analysisCheckpointIndex,
  analysisCheckpointState,
  analysisEtaLabel,
  nextDisplayedProgress,
} from './analysisProgress.ts'

test('keeps a failed AI run at the semantic-analysis checkpoint', () => {
  const current = analysisCheckpointIndex('ANALYSIS_PAUSED', 'ANALYSIS_PAUSED', 72)
  assert.equal(current, 4)
  assert.deepEqual(
    Array.from({ length: 7 }, (_, index) => analysisCheckpointState(index, current, 'ANALYSIS_PAUSED')),
    ['done', 'done', 'done', 'done', 'paused', 'pending', 'pending'],
  )
})

test('uses exact live checkpoints while analysis is running', () => {
  const current = analysisCheckpointIndex('FACT_VALIDATION', 'ANALYZING', 82)
  assert.equal(current, 5)
  assert.equal(analysisCheckpointState(4, current, 'ANALYZING'), 'done')
  assert.equal(analysisCheckpointState(5, current, 'ANALYZING'), 'active')
  assert.equal(analysisCheckpointState(6, current, 'ANALYZING'), 'pending')
})

test('uses durable progress while a resumed task still carries the paused checkpoint code', () => {
  assert.equal(analysisCheckpointIndex('ANALYSIS_PAUSED', 'ANALYZING', 72), 4)
})

test('moves continuously within a checkpoint without claiming completion', () => {
  let displayed = 72
  for (let tick = 0; tick < 180; tick += 1) {
    displayed = nextDisplayedProgress({
      current: displayed,
      authoritative: 72,
      checkpoint: 'AI_SEMANTIC_ANALYSIS',
      status: 'ANALYZING',
      deltaMs: 400,
    })
  }

  assert.ok(displayed > 72, `expected progress to move beyond 72, received ${displayed}`)
  assert.ok(displayed <= 84, `expected the AI checkpoint ceiling to be respected, received ${displayed}`)
})

test('smoothly catches up to newer server progress and never reaches 100 while running', () => {
  const caughtUp = nextDisplayedProgress({
    current: 52,
    authoritative: 72,
    checkpoint: 'AI_SEMANTIC_ANALYSIS',
    status: 'ANALYZING',
    deltaMs: 400,
  })

  assert.ok(caughtUp > 52)
  assert.ok(caughtUp < 72)
  assert.equal(nextDisplayedProgress({
    current: 98,
    authoritative: 99,
    checkpoint: 'REPORT_READY',
    status: 'ANALYZING',
    deltaMs: 400,
  }), 98)
  assert.equal(nextDisplayedProgress({
    current: 98,
    authoritative: 100,
    checkpoint: 'REPORT_READY',
    status: 'COMPLETED',
    deltaMs: 400,
  }), 100)
})

test('stops cosmetic progress when analysis is paused', () => {
  assert.equal(nextDisplayedProgress({
    current: 76.4,
    authoritative: 72,
    checkpoint: 'ANALYSIS_PAUSED',
    status: 'ANALYSIS_PAUSED',
    deltaMs: 400,
  }), 76.4)
})

test('uses conservative ETA ranges and explains a long provider wait', () => {
  assert.equal(
    analysisEtaLabel('AI_SEMANTIC_ANALYSIS', 'ANALYZING', 4_000),
    '预计还需约 30–45 秒',
  )
  assert.equal(
    analysisEtaLabel('AI_SEMANTIC_ANALYSIS', 'ANALYZING', 65_000),
    '正在等待 AI 返回，通常还需 1–2 分钟',
  )
  assert.equal(
    analysisEtaLabel('FACT_VALIDATION', 'ANALYSIS_PAUSED', 65_000),
    '进度已保存，可随时恢复',
  )
})

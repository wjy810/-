import assert from 'node:assert/strict'
import test from 'node:test'
import { displayJobMatchText, jobMatchStatusTone } from './reportPresentation.ts'

test('turns provider-facing wording into readable Chinese without stray spaces', () => {
  assert.equal(
    displayJobMatchText('ruleResult 判定为 NOT_FOUND，且当前 evidence 为空'),
    '规则引擎判定为暂未发现证据，且当前证据为空',
  )
  assert.equal(
    displayJobMatchText('两项硬门槛均为 NOT_FOUND，且当前 evidence 为空'),
    '两项硬门槛均为暂未发现证据，且当前证据为空',
  )
  assert.equal(
    displayJobMatchText('ruleResult 因此判定未满足，当前没有 evidence 支持'),
    '规则结果因此判定未满足，当前没有证据支持',
  )
})

test('localizes report states and preserves their semantic tone', () => {
  assert.equal(displayJobMatchText('RESUME_SUPPORTED'), '简历已体现')
  assert.equal(displayJobMatchText('EVIDENCE_GAP'), '证据缺口')
  assert.equal(jobMatchStatusTone('RESUME_SUPPORTED'), 'green')
  assert.equal(jobMatchStatusTone('NOT_FOUND'), 'orange')
})

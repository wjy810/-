import assert from 'node:assert/strict'
import test from 'node:test'
import {
  confidenceLevel,
  confidencePresentation,
  displayJobMatchText,
  evidenceSourceLabel,
  jobMatchStatusTone,
  recommendationLabel,
} from './reportPresentation.ts'

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
  assert.equal(displayJobMatchText('BEFORE_INTERVIEW'), '面试前')
  assert.equal(jobMatchStatusTone('RESUME_SUPPORTED'), 'success')
  assert.equal(jobMatchStatusTone('NOT_FOUND'), 'accent')
})

test('hides unlabelled internal codes instead of showing them', () => {
  assert.equal(displayJobMatchText('SOME_INTERNAL_CODE'), '')
  assert.equal(displayJobMatchText('SQL'), 'SQL')
  assert.equal(displayJobMatchText('熟悉 Spring Boot'), '熟悉 Spring Boot')
})

test('gives no default verdict when the report has no recommendation', () => {
  assert.equal(recommendationLabel('CONDITIONAL'), '准备后投递')
  assert.equal(recommendationLabel(undefined), '')
  assert.equal(recommendationLabel('SOMETHING_ELSE'), '')
})

test('names evidence by source kind and title, never by an id fragment', () => {
  const sources = [
    { id: '7f3c2a10-1111-2222-3333-444455556666', sourceType: 'CAREER_RECORD', title: '订单系统重构项目' },
    { id: '9a8b7c6d-1111-2222-3333-444455556666', sourceType: 'CAREER_FILE', title: '' },
  ]
  assert.equal(evidenceSourceLabel('7f3c2a10-1111-2222-3333-444455556666', sources), '资料库记录：订单系统重构项目')
  assert.equal(evidenceSourceLabel('9a8b7c6d-1111-2222-3333-444455556666', sources), '资料库文件')
  const missing = evidenceSourceLabel('deadbeef-0000-0000-0000-000000000000', sources)
  assert.equal(missing, '已授权资料（详情不可用）')
  assert.doesNotMatch(missing, /deadbeef/)
})

test('confidence is a level, also for reports that stored a number', () => {
  assert.equal(confidenceLevel('MEDIUM'), 'MEDIUM')
  assert.equal(confidenceLevel(86), 'HIGH')
  assert.equal(confidenceLevel(61), 'MEDIUM')
  assert.equal(confidenceLevel(40), 'LOW')
  assert.equal(confidenceLevel(undefined), null)
  assert.equal(confidencePresentation('LOW')?.tone, 'danger')
  assert.equal(recommendationLabel('PREPARE_FIRST'), '先补强再投递')
  assert.equal(displayJobMatchText('UNCLEAR'), '无法判断')
})

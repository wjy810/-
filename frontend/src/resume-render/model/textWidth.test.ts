import assert from 'node:assert/strict'
import test from 'node:test'
import { estimateEm, skillLabelEm } from './textWidth.ts'

test('full-width characters count a full em, Latin about half', () => {
  assert.equal(estimateEm('语言与框架'), 5)
  assert.ok(Math.abs(estimateEm('Systems') - 7 * 0.56) < 1e-9)
  assert.equal(estimateEm('工具：'), 3)
})

test('skill sections share the widest label plus its colon', () => {
  assert.equal(skillLabelEm(['数据', '数据与中间件', '工具'], 'zh-CN'), 7)
  assert.equal(skillLabelEm(['Languages', 'Systems'], 'en'), Math.round((9 * 0.56 + 0.3) * 100) / 100)
})

test('very long labels are capped and unnamed groups need no column', () => {
  assert.equal(skillLabelEm(['一个特别特别特别长的技能分类名称'], 'zh-CN'), 9)
  assert.equal(skillLabelEm(['', ''], 'zh-CN'), null)
})

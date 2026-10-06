import assert from 'node:assert/strict'
import test from 'node:test'
import {
  careerWorkbenchDirection,
  careerWorkbenchQueryValue,
  careerWorkbenchViewFromQuery,
  resolveCareerWorkbenchKey,
} from './workbenchTransition.ts'

test('career workbench query values restore only supported views', () => {
  assert.equal(careerWorkbenchViewFromQuery('canvas'), 'CANVAS')
  assert.equal(careerWorkbenchViewFromQuery('validation'), 'VALIDATION')
  assert.equal(careerWorkbenchViewFromQuery('unknown'), null)
  assert.equal(careerWorkbenchViewFromQuery(['plan']), null)
  assert.equal(careerWorkbenchQueryValue('HISTORY'), 'history')
})

test('career workbench direction follows the visible tab order', () => {
  assert.equal(careerWorkbenchDirection('CANVAS', 'PLAN'), 'forward')
  assert.equal(careerWorkbenchDirection('HISTORY', 'VALIDATION'), 'backward')
  assert.equal(careerWorkbenchDirection('PLAN', 'PLAN'), 'none')
})

test('career workbench keyboard navigation wraps and supports Home and End', () => {
  assert.equal(resolveCareerWorkbenchKey('HISTORY', 'ArrowRight'), 'CANVAS')
  assert.equal(resolveCareerWorkbenchKey('CANVAS', 'ArrowLeft'), 'HISTORY')
  assert.equal(resolveCareerWorkbenchKey('VALIDATION', 'Home'), 'CANVAS')
  assert.equal(resolveCareerWorkbenchKey('PLAN', 'End'), 'HISTORY')
  assert.equal(resolveCareerWorkbenchKey('PLAN', 'Enter'), null)
})

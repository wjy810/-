import assert from 'node:assert/strict'
import test from 'node:test'
import {
  clampMatchScore,
  reportTabFromQuery,
  stepperTrackProgress,
  wizardTransitionName,
} from './uiPresentation.ts'

test('stepper track progresses only between the four durable creation steps', () => {
  assert.equal(stepperTrackProgress(0), 0)
  assert.equal(stepperTrackProgress(1), 0)
  assert.equal(stepperTrackProgress(2), 25)
  assert.equal(stepperTrackProgress(3), 50)
  assert.equal(stepperTrackProgress(4), 75)
  assert.equal(stepperTrackProgress(9), 75)
})

test('wizard transitions distinguish forward, backward and unchanged movement', () => {
  assert.equal(wizardTransitionName(1, 2), 'jm-stage-forward')
  assert.equal(wizardTransitionName(3, 2), 'jm-stage-backward')
  assert.equal(wizardTransitionName(2, 2), 'jm-stage-fade')
})

test('report tab query restoration accepts only published report tabs', () => {
  assert.equal(reportTabFromQuery('strengths'), 'strengths')
  assert.equal(reportTabFromQuery('plan'), 'plan')
  assert.equal(reportTabFromQuery(['gaps']), 'overview')
  assert.equal(reportTabFromQuery('unknown'), 'overview')
  assert.equal(reportTabFromQuery(undefined), 'overview')
})

test('score presentation stays inside the visual ring range', () => {
  assert.equal(clampMatchScore(-20), 0)
  assert.equal(clampMatchScore(0), 0)
  assert.equal(clampMatchScore(8), 8)
  assert.equal(clampMatchScore(68.4), 68)
  assert.equal(clampMatchScore(100), 100)
  assert.equal(clampMatchScore(140), 100)
  assert.equal(clampMatchScore(Number.NaN), 0)
})

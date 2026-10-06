import assert from 'node:assert/strict'
import { test } from 'node:test'
import { sceneQuality, studioEntry, studioWind } from './evidenceSceneMath.ts'

test('first entry establishes paper, then evidence, then path, ending at 1.2 seconds', () => {
  assert.deepEqual(studioEntry(0), { resume: 0, project: 0, skills: 0, path: 0 })
  assert.ok(studioEntry(0.2).resume > 0)
  assert.equal(studioEntry(0.2).project, 0)
  assert.ok(studioEntry(0.5).project > studioEntry(0.5).skills)
  assert.equal(studioEntry(0.5).path, 0)
  assert.deepEqual(studioEntry(1.2), { resume: 1, project: 1, skills: 1, path: 1 })
})
test('weather field is deterministic, bounded, and continuous across cell boundaries', () => {
  for (let t = 0; t < 100; t += 0.125) {
    assert.equal(studioWind(t), studioWind(t))
    assert.ok(Math.abs(studioWind(t)) <= 1)
    assert.ok(Math.abs(studioWind(t + 0.001) - studioWind(t)) < 0.001)
    assert.ok(Math.abs(studioWind(t, 0.01) - studioWind(t, 0)) < 0.01)
  }
})
test('only Cinematic allocates postprocessing and each quality has its explicit DPR cap', () => {
  assert.deepEqual(Object.values(sceneQuality).map(item => item.dpr), [1.25, 1.5, 2])
  assert.deepEqual(Object.values(sceneQuality).map(item => item.postprocessing), [false, false, true])
})

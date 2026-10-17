import assert from 'node:assert/strict'
import test from 'node:test'
import { AUTO_FIT, fittedRhythm } from './design.ts'

const design = (spacing: string, lineHeight: string) => ({ spacing, lineHeight }) as never

test('auto-fit level 0 keeps the designed rhythm', () => {
  assert.deepEqual(fittedRhythm(design('NORMAL', 'NORMAL'), 0), { gap: 1, leading: 1.55 })
})

test('full auto-fit tightens spacing and line height within the floor', () => {
  const tight = fittedRhythm(design('NORMAL', 'NORMAL'), 1)
  assert.ok(Math.abs(tight.gap - AUTO_FIT.minGapShare) < 1e-9)
  assert.ok(Math.abs(tight.leading - 1.45) < 1e-9)
  // Already compact leading never drops below the floor, and never grows.
  assert.equal(fittedRhythm(design('TIGHT', 'COMPACT'), 1).leading, AUTO_FIT.minLeading)
  assert.ok(fittedRhythm(design('TIGHT', 'COMPACT'), 0.5).leading <= 1.42)
})

import assert from 'node:assert/strict'
import test from 'node:test'
import { breakableChunks } from './breaks.ts'

test('e-mail addresses break after @ and before dots', () => {
  assert.deepEqual(breakableChunks('ouyang.siyuan@alumni.example.com'), ['ouyang', '.siyuan@', 'alumni', '.example', '.com'])
})

test('links break before path separators and keep the text intact', () => {
  const label = 'github.com/ouyang-siyuan/portfolio'
  assert.deepEqual(breakableChunks(label), ['github', '.com', '/ouyang-siyuan', '/portfolio'])
  assert.equal(breakableChunks(label).join(''), label)
})

test('labels without separators stay whole', () => {
  assert.deepEqual(breakableChunks('linxiao'), ['linxiao'])
  assert.deepEqual(breakableChunks(''), [])
})

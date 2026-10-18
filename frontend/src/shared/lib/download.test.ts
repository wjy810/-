import assert from 'node:assert/strict'
import test from 'node:test'
import { safeFilename } from './download.ts'

test('filenames lose reserved characters and never end up empty', () => {
  assert.equal(safeFilename('兑换码-2027/届:计算机*学院.csv'), '兑换码-2027_届_计算机_学院.csv')
  assert.equal(safeFilename('  ..  '), 'download')
  assert.equal(safeFilename('', 'export.zip'), 'export.zip')
  assert.equal(safeFilename('a'.repeat(200)).length, 120)
})

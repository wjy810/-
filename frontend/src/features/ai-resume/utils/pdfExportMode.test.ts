import assert from 'node:assert/strict'
import test from 'node:test'
import { needsPdfContactWarning } from './pdfExportMode.ts'

test('requires a contact method only for the standard PDF', () => {
  assert.equal(needsPdfContactWarning('STANDARD', {}), true)
  assert.equal(needsPdfContactWarning('STANDARD', { email: 'seeker@example.com' }), false)
  assert.equal(needsPdfContactWarning('ANONYMOUS', {}), false)
})

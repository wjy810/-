import assert from 'node:assert/strict'
import test from 'node:test'
import { needsPdfContactWarning, pdfFallbackFilename } from './pdfExportMode.ts'

test('requires a contact method only for the standard PDF', () => {
  assert.equal(needsPdfContactWarning('STANDARD', {}), true)
  assert.equal(needsPdfContactWarning('STANDARD', { email: 'seeker@example.com' }), false)
  assert.equal(needsPdfContactWarning('ANONYMOUS', {}), false)
})

test('uses a distinguishable anonymous PDF fallback filename', () => {
  assert.equal(pdfFallbackFilename('STANDARD'), 'JobProof-resume.pdf')
  assert.equal(pdfFallbackFilename('ANONYMOUS'), 'JobProof-resume-anonymous.pdf')
})

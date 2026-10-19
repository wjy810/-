import assert from 'node:assert/strict'
import test from 'node:test'
import { FIELD_OF_REASON, MAIL_PRESETS, presetFor } from './presets.ts'

test('presets cover the common providers and are found again from the host', () => {
  assert.equal(MAIL_PRESETS[0]!.host, 'smtp.qq.com')
  assert.equal(presetFor(' SMTP.QQ.com ')?.key, 'qq')
  assert.equal(presetFor('smtp.example.com'), undefined)
  assert.ok(MAIL_PRESETS.every(preset => preset.port > 0 && preset.hint.length > 0))
})

test('field-level refusals land next to their field', () => {
  assert.equal(FIELD_OF_REASON.MAIL_FROM_MISMATCH, 'fromAddress')
  assert.equal(FIELD_OF_REASON.MAIL_PASSWORD_REQUIRED, 'password')
  assert.equal(FIELD_OF_REASON.VERSION_CONFLICT, undefined)
})

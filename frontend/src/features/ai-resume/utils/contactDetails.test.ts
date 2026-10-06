import assert from 'node:assert/strict'
import test from 'node:test'
import { contactSubmitIssue, validContactEmail, validContactPhone } from './contactDetails.ts'

test('requires a name and at least one contact method', () => {
  assert.equal(contactSubmitIssue({ email: 'seeker@example.com' }), '请填写姓名。')
  assert.equal(contactSubmitIssue({ name: '林知远' }), '邮箱和手机号请至少填写一项。')
  assert.equal(contactSubmitIssue({ name: '林知远', email: 'seeker@example.com' }), '')
  assert.equal(contactSubmitIssue({ name: '林知远', phone: '138 0000 1111' }), '')
})

test('validates international-friendly email and phone formats', () => {
  assert.equal(validContactEmail('lin.zhiyuan@example.com'), true)
  assert.equal(validContactEmail('lin.example.com'), false)
  assert.equal(validContactPhone('+86 138-0000-1111'), true)
  assert.equal(validContactPhone('123'), false)
  assert.equal(contactSubmitIssue({ name: '林知远', email: 'bad-email' }), '请填写有效的邮箱地址。')
  assert.equal(contactSubmitIssue({ name: '林知远', phone: 'abc-1234567' }), '请填写有效的手机号，可包含国家或地区代码。')
})

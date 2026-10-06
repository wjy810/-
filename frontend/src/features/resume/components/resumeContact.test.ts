import assert from 'node:assert/strict'
import test from 'node:test'
import { formatResumeContactLine } from './resumeContact.ts'

test('formats contact fields and personal links in PDF renderer order', () => {
  assert.equal(formatResumeContactLine({
    phone: '13800000000',
    email: 'demo@jobproof.local',
    location: '武汉',
    links: ['https://portfolio.example.com', { url: 'https://github.com/jobproof-demo' }],
  }), '13800000000 · demo@jobproof.local · 武汉 · https://portfolio.example.com · https://github.com/jobproof-demo')
})

test('ignores empty and unsupported link values', () => {
  assert.equal(formatResumeContactLine({
    email: 'demo@jobproof.local',
    links: ['', null, { href: 'https://example.com' }, { label: 'missing URL' }],
  }), 'demo@jobproof.local · https://example.com')
})

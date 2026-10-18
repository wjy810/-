import assert from 'node:assert/strict'
import test from 'node:test'
import { batchStatus, benefits, codesCsv, endOfDayIso, formatCodeInput, fullCode, isCompleteCode, perMonth, planLimitCopy, redemptionRate, saving, yuan } from './labels.ts'

const MONTH = { code: 'MONTH' as const, label: '月付', amountFen: 1900, days: 30 }
const QUARTER = { code: 'QUARTER' as const, label: '季付', amountFen: 4900, days: 90 }
const YEAR = { code: 'YEAR' as const, label: '年付', amountFen: 16800, days: 365 }

test('prices read in yuan, per month and as a saving against monthly', () => {
  assert.equal(yuan(1900), '19')
  assert.equal(yuan(4950), '49.5')
  assert.equal(perMonth(YEAR), '13.8')
  assert.equal(saving(QUARTER, MONTH), 14)
  assert.equal(saving(YEAR, MONTH), 27)
  assert.equal(saving(MONTH, MONTH), 0)
})

test('codes are regrouped while typing and only complete ones can be sent', () => {
  assert.equal(formatCodeInput('jp abcd efgh'), 'ABCD-EFGH')
  assert.equal(formatCodeInput('abcdefghjkmnpqrsTOO-LONG'), 'ABCD-EFGH-JKMN-PQRS')
  assert.equal(formatCodeInput('JP-'), '')
  assert.equal(formatCodeInput('JP-JPAB-CDEF-GHJK-MNPQ'), 'JPAB-CDEF-GHJK-MNPQ')
  assert.equal(formatCodeInput('JPAB-CDEF-GHJK-MNPQ'), 'JPAB-CDEF-GHJK-MNPQ')
  assert.equal(formatCodeInput('jpabcdefghjkmnpqrs'), 'ABCD-EFGH-JKMN-PQRS')
  assert.equal(isCompleteCode('ABCD-EFGH-JKMN-PQRS'), true)
  assert.equal(isCompleteCode('ABCD-EFGH-JKMN'), false)
  assert.equal(fullCode('ABCD-EFGH-JKMN-PQRS'), 'JP-ABCD-EFGH-JKMN-PQRS')
})

test('the plan comparison uses the numbers the server reports', () => {
  const rows = benefits({ plan: 'FREE', aiMonthlyUnits: 50, activeLinks: 3 }, { plan: 'PRO', aiMonthlyUnits: 300, activeLinks: 20 })
  assert.deepEqual(rows.slice(0, 2).map(row => [row.free, row.pro]), [['50 次', '300 次'], ['3 条', '20 条']])
})

test('batch status, redemption rate and the redeem-before day', () => {
  assert.deepEqual(batchStatus('REVOKED'), { label: '已停用', tone: 'danger' })
  assert.equal(batchStatus('SOMETHING').label, 'SOMETHING')
  assert.equal(redemptionRate(1, 3), 33)
  assert.equal(redemptionRate(0, 0), 0)
  assert.equal(endOfDayIso('not a day'), null)
  const end = new Date(endOfDayIso('2026-11-30') as string)
  assert.equal(end.getDate(), 30)
  assert.equal(end.getHours(), 23)
  assert.equal(end.getMinutes(), 59)
})

test('the codes CSV quotes every cell and defuses formulas', () => {
  const csv = codesCsv({ name: '=HYPERLINK("x")', channel: '某某大学', durationDays: 30 }, ['JP-AAAA-BBBB-CCCC-DDDD'])
  assert.ok(csv.startsWith('﻿"兑换码","批次","渠道","专业版天数"\r\n'))
  assert.ok(csv.includes('"JP-AAAA-BBBB-CCCC-DDDD","\'=HYPERLINK(""x"")","某某大学","30"'))
})

test('the plan-limit nudge quotes the account\'s own numbers', () => {
  const free = { plan: 'FREE' as const, aiMonthlyUnits: 30, activeLinks: 3 }
  const pro = { plan: 'PRO' as const, aiMonthlyUnits: 300, activeLinks: 20 }
  assert.equal(planLimitCopy('RESUME_LINK_LIMIT', free, pro).title, '免费版最多同时保留 3 条有效链接')
  assert.ok(planLimitCopy('AI_QUOTA_INSUFFICIENT', free, pro).description.includes('300 次'))
})

import assert from 'node:assert/strict'
import { readdirSync, readFileSync } from 'node:fs'
import { join } from 'node:path'
import test from 'node:test'
import {
  buildCalendarDays,
  datetimeValue,
  formatDateTimeDisplay,
  formatMonthDisplay,
  parseDateTimeValue,
} from './datePicker.ts'

test('builds a Monday-first six-week calendar without losing adjacent days', () => {
  const days = buildCalendarDays(2026, 7, '2026-08-24', new Date(2026, 7, 24))
  assert.equal(days.length, 42)
  assert.equal(days[0]?.value, '2026-07-27')
  assert.equal(days[41]?.value, '2026-09-06')
  assert.equal(days.find((day) => day.selected)?.today, true)
})

test('parses, formats and serializes local date-time values without timezone conversion', () => {
  const parsed = parseDateTimeValue('2026-08-24T09:05')
  assert.deepEqual(parsed, { year: 2026, month: 7, day: 24, hour: 9, minute: 5 })
  assert.equal(datetimeValue(parsed!), '2026-08-24T09:05')
  assert.equal(formatDateTimeDisplay('2026-08-24T09:05'), '2026年8月24日 09:05')
  assert.equal(formatMonthDisplay('2026-08'), '2026年8月')
})

test('all Vue date controls use the shared calendar instead of native picker popups', () => {
  const sourceRoot = join(import.meta.dirname, '..', '..')
  const offenders: string[] = []

  function scan(directory: string): void {
    for (const entry of readdirSync(directory, { withFileTypes: true })) {
      const path = join(directory, entry.name)
      if (entry.isDirectory()) scan(path)
      else if (entry.name.endsWith('.vue')) {
        const source = readFileSync(path, 'utf8')
        if (/type=["'](?:date|month|datetime-local)["']|\.showPicker\s*\(/.test(source)) offenders.push(path)
      }
    }
  }

  scan(sourceRoot)
  assert.deepEqual(offenders, [])
})

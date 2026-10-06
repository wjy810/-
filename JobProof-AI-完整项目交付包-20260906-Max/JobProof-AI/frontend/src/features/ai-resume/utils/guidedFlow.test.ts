import assert from 'node:assert/strict'
import test from 'node:test'
import { suggestGuidedCard } from './guidedFlow.ts'

type Card = { cardType: string; status: string }

function cards(statuses: Partial<Record<string, string>> = {}): Card[] {
  return ['TARGET_JOB', 'EDUCATION', 'EXPERIENCE', 'PROJECTS'].map((cardType) => ({
    cardType,
    status: statuses[cardType] ?? 'IDLE',
  }))
}

test('student onboarding asks for education before the target job', () => {
  assert.equal(suggestGuidedCard('STUDENT', cards())?.cardType, 'EDUCATION')
})

test('graduate onboarding asks for education before the target job', () => {
  assert.equal(suggestGuidedCard('GRADUATE', cards())?.cardType, 'EDUCATION')
})

test('student onboarding asks for the target job after education is confirmed', () => {
  assert.equal(suggestGuidedCard('STUDENT', cards({ EDUCATION: 'CONFIRMED' }))?.cardType, 'TARGET_JOB')
})

test('professional onboarding keeps the target job first', () => {
  assert.equal(suggestGuidedCard('PROFESSIONAL', cards())?.cardType, 'TARGET_JOB')
})

test('skipped optional cards do not block the guided flow', () => {
  const optionalCards: Card[] = [
    { cardType: 'LANGUAGES', status: 'SKIPPED' },
    { cardType: 'SUMMARY', status: 'IDLE' },
  ]
  assert.equal(suggestGuidedCard('PROFESSIONAL', optionalCards)?.cardType, 'SUMMARY')
})

import assert from 'node:assert/strict'
import test from 'node:test'
import { isNewUser, nextStep, readiness } from './nextStep.ts'
import type { WorkspaceOverview } from './types.ts'

function overview(patch: Partial<WorkspaceOverview> = {}): WorkspaceOverview {
  return {
    profile: { completeness: 40, missing: [] },
    resumes: { total: 0, exportable: 0, recent: [] },
    jobMatches: { total: 0, completed: 0, recent: [] },
    mockInterviews: { total: 0, completed: 0, averageScore: null, resumable: null },
    careerCanvases: { total: 0, recent: [] },
    unreadNotifications: 0,
    degraded: [],
    ...patch,
  }
}

const resume = (confirmedModules: number) => ({ id: 'r1', title: '后端实习简历', status: 'DRAFT', updatedAt: null, conversationId: 'c1', templateName: '校园新锐', confirmedModules, totalModules: 11 })

test('a brand new user is sent to write the first resume', () => {
  const value = overview()
  assert.equal(nextStep(value).kind, 'create-resume')
  assert.equal(nextStep(value).to, '/ai-resume/new')
  assert.equal(isNewUser(value), true)
})

test('an unfinished resume is continued before anything else', () => {
  const step = nextStep(overview({ resumes: { total: 1, exportable: 0, recent: [resume(2)] } }))
  assert.equal(step.kind, 'continue-resume')
  assert.match(step.description, /2 \/ 11/)
})

test('after a solid resume the user is pointed at matching, then interviews, then planning', () => {
  const base = { resumes: { total: 1, exportable: 1, recent: [resume(8)] } }
  assert.equal(nextStep(overview(base)).kind, 'first-match')
  const withMatch = { ...base, jobMatches: { total: 1, completed: 1, recent: [{ id: 'm1', title: 'Java 后端', company: null, status: 'COMPLETED', score: 78, updatedAt: null }] } }
  assert.equal(nextStep(overview(withMatch)).kind, 'practice-interview')
  assert.match(nextStep(overview(withMatch)).title, /Java 后端/)
  const withInterview = { ...withMatch, mockInterviews: { total: 1, completed: 1, averageScore: 80, resumable: null } }
  assert.equal(nextStep(overview(withInterview)).kind, 'plan-career')
})

test('degraded modules (null) never crash the rules', () => {
  const value = overview({ resumes: null, jobMatches: null, mockInterviews: null, careerCanvases: null, profile: null, degraded: ['resume'] })
  assert.equal(typeof nextStep(value).to, 'string')
  assert.equal(readiness(value).score, 0)
})

test('readiness averages the four preparation steps', () => {
  const value = overview({
    profile: { completeness: 100, missing: [] },
    resumes: { total: 1, exportable: 1, recent: [resume(8)] },
  })
  assert.equal(readiness(value).score, 50)
})

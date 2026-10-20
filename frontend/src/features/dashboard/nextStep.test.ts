import assert from 'node:assert/strict'
import test from 'node:test'
import { isNewUser, needsDirection, nextStep, readiness } from './nextStep.ts'
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

const applications = (total: number, linked: number) => ({
  stats: { total, active: total, applied: total, linked, opened: 0, interviews: 0, offers: 0, overdue: 0, upcoming: 0 },
  recentOpens: [],
  nextSteps: [],
})

test('readiness averages the five preparation steps', () => {
  const value = overview({
    profile: { completeness: 100, missing: [] },
    resumes: { total: 1, exportable: 1, recent: [resume(8)] },
  })
  assert.equal(readiness(value).score, 40)
  assert.equal(readiness({ ...value, applications: applications(2, 0) }).score, 50)
  assert.equal(readiness({ ...value, applications: applications(2, 1) }).items.find(item => item.key === 'apply')?.done, true)
})

test('with an exportable resume and a match, the next step is to send it with a link', () => {
  const base = {
    resumes: { total: 1, exportable: 1, recent: [resume(8)] },
    jobMatches: { total: 1, completed: 1, recent: [] },
  }
  assert.equal(nextStep(overview({ ...base, applications: applications(0, 0) })).kind, 'track-application')
  assert.equal(nextStep(overview({ ...base, applications: applications(0, 0) })).to, '/applications?new=1')
  assert.equal(nextStep(overview({ ...base, applications: applications(1, 1) })).kind, 'practice-interview')
})

test('without a target job the profile step leads to onboarding', () => {
  const without = overview({ profile: { completeness: 50, missing: ['目标岗位', '职业简介'] } })
  assert.equal(needsDirection(without), true)
  const step = readiness(without).items.find(item => item.key === 'profile')
  assert.equal(step?.to, '/onboarding')
  assert.equal(step?.label, '确定求职方向')
  const set = overview({ profile: { completeness: 80, missing: ['职业简介'] } })
  assert.equal(needsDirection(set), false)
  assert.equal(readiness(set).items.find(item => item.key === 'profile')?.to, '/career-library')
  assert.equal(needsDirection(overview({ profile: null })), false)
})

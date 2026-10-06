import test from 'node:test'
import assert from 'node:assert/strict'
import type { CareerPlanningSession } from '../types'
import { shouldAcceptCareerPlanningSnapshot } from './sessionSnapshot.ts'

function snapshot(overrides: {
  id?: string
  version?: number
  profileVersion?: number
  canvasVersion?: number | null
  activeGoalId?: string | null
} = {}): CareerPlanningSession {
  const canvasVersion = overrides.canvasVersion === undefined ? 4 : overrides.canvasVersion
  const activeGoalId = overrides.activeGoalId === undefined ? 'goal-1' : overrides.activeGoalId
  return {
    id: overrides.id ?? 'session-1',
    status: 'ACTIVE',
    phase: 'PROFILE',
    entryMode: 'AI_DISCOVERY',
    aiConsent: true,
    version: overrides.version ?? 8,
    profile: {
      id: 'profile-1', status: 'DRAFT', entryMode: 'AI_DISCOVERY', basics: {}, preferences: {}, constraints: {},
      snapshotVersion: 0, version: overrides.profileVersion ?? 5, items: [], updatedAt: '2026-08-28T00:00:00Z',
    },
    permissions: [], interviewRounds: [], messages: [],
    activeGoal: activeGoalId === null ? null : {
      id: activeGoalId,
      recommendationId: `recommendation-${activeGoalId}`,
      taxonomyNodeId: `taxonomy-${activeGoalId}`,
      title: activeGoalId === 'goal-2' ? 'Java 后端工程师' : '后端开发工程师',
      status: 'ACTIVE',
      version: 1,
      confirmedAt: '2026-08-28T00:00:00Z',
    },
    canvas: canvasVersion === null ? null : {
      versionId: `canvas-${canvasVersion}`, version: canvasVersion, reason: 'USER_EDIT', graphHash: 'hash',
      nodes: [], relations: [], createdAt: '2026-08-28T00:00:00Z',
    },
    createdAt: '2026-08-28T00:00:00Z', updatedAt: '2026-08-28T00:00:00Z',
  }
}

test('career planning snapshots never regress session, profile or canvas versions', () => {
  const current = snapshot()
  assert.equal(shouldAcceptCareerPlanningSnapshot(current, snapshot({ version: 7 })), false)
  assert.equal(shouldAcceptCareerPlanningSnapshot(current, snapshot({ profileVersion: 4 })), false)
  assert.equal(shouldAcceptCareerPlanningSnapshot(current, snapshot({ canvasVersion: 3 })), false)
  assert.equal(shouldAcceptCareerPlanningSnapshot(current, snapshot({ version: 9, profileVersion: 6, canvasVersion: 5 })), true)
})

test('a new session and a deliberate higher-version canvas reset remain acceptable', () => {
  const current = snapshot()
  assert.equal(shouldAcceptCareerPlanningSnapshot(current, snapshot({ id: 'session-2', version: 1, profileVersion: 1 })), true)
  assert.equal(shouldAcceptCareerPlanningSnapshot(current, snapshot({ version: 9, profileVersion: 5, canvasVersion: null })), true)
})

test('an established canvas cannot change its goal inside the same session', () => {
  const current = snapshot({ version: 8, canvasVersion: 7, activeGoalId: 'goal-1' })

  assert.equal(shouldAcceptCareerPlanningSnapshot(current, snapshot({
    version: 9,
    canvasVersion: 1,
    activeGoalId: 'goal-2',
  })), false)
})

test('the first confirmed goal is accepted without weakening version protection', () => {
  const current = snapshot({ version: 8, canvasVersion: null, activeGoalId: null })

  assert.equal(shouldAcceptCareerPlanningSnapshot(current, snapshot({
    version: 9,
    canvasVersion: 1,
    activeGoalId: 'goal-2',
  })), true)
})

import type {
  CanvasVersion, CareerAbilityValidation, CareerCanvas, CareerCanvasProposal,
  CareerLearningPlan, CareerPlanningSession,
} from '@/features/career-planning/types'

export function makeCanvas(version = 3): CareerCanvas {
  return {
    versionId: `canvas-v${version}`,
    version,
    parentVersionId: version > 1 ? `canvas-v${version - 1}` : null,
    reason: 'USER_EDIT',
    changeSummary: '更新能力节点',
    graphHash: `hash-${version}`,
    promptVersion: 'cp-v1',
    schemaVersion: 'career-canvas-v1',
    model: 'mock-model',
    createdAt: '2026-08-28T00:00:00Z',
    nodes: [
      { logicalNodeId: 'career', type: 'CAREER', status: 'PLANNED', title: '后端开发', detail: {}, sourceRefs: [], x: 800, y: 200, locked: true, sortOrder: 0 },
      { logicalNodeId: 'domain-java', type: 'DOMAIN', status: 'NOT_STARTED', title: 'Java 核心', detail: { summary: 'Java 基础能力' }, sourceRefs: ['profile:1'], x: 520, y: 160, locked: false, sortOrder: 1 },
      { logicalNodeId: 'skill-oop', type: 'SKILL', status: 'PENDING_VALIDATION', title: '面向对象建模', detail: { summary: '领域建模能力', masteryCriteria: ['职责清晰', '接口隔离'] }, sourceRefs: ['profile:1'], x: 240, y: 120, locked: false, sortOrder: 2 },
      { logicalNodeId: 'task-api', type: 'TASK', status: 'PLANNED', title: '实现状态流转 API', detail: { summary: '完成可测试接口' }, sourceRefs: ['record:1'], x: 240, y: 240, locked: false, sortOrder: 3 },
    ],
    relations: [
      { id: 'r1', fromNodeId: 'domain-java', toNodeId: 'career', type: 'TREE_PARENT' },
      { id: 'r2', fromNodeId: 'skill-oop', toNodeId: 'domain-java', type: 'TREE_PARENT' },
      { id: 'r3', fromNodeId: 'task-api', toNodeId: 'domain-java', type: 'TREE_PARENT' },
    ],
  }
}

export function makePlan(canvas = makeCanvas()): CareerLearningPlan {
  return {
    id: 'plan-1', sessionId: 'session-1', goalId: 'goal-1', canvasVersionId: canvas.versionId,
    canvasVersion: canvas.version, durationWeeks: 4, intensity: 'STANDARD', weeklyHours: 8,
    learningDays: [1, 3, 6], startDate: '2026-08-31', targetDate: '2026-09-27', status: 'ACTIVE',
    version: 2, generationMethod: 'AI', promptVersion: 'cp-v1', schemaVersion: 'plan-v1', model: 'mock-model',
    tasks: [
      { id: 'task-1', nodeId: 'skill-oop', taskType: 'LEARNING', week: 1, title: '完成领域建模练习', description: '划分类职责', priority: 'HIGH', estimatedMinutes: 90, dueDate: '2026-09-02', status: 'TODO', evidenceRequired: true, sortOrder: 0, version: 1, createdAt: '2026-08-28T00:00:00Z', updatedAt: '2026-08-28T00:00:00Z' },
      { id: 'task-2', nodeId: 'task-api', taskType: 'CHECKPOINT', week: 1, title: '实现 API', description: '完成接口与测试', priority: 'MEDIUM', estimatedMinutes: 120, dueDate: '2026-09-04', status: 'TODO', evidenceRequired: false, sortOrder: 1, version: 1, createdAt: '2026-08-28T00:00:00Z', updatedAt: '2026-08-28T00:00:00Z' },
    ],
    evidences: [], reviews: [], createdAt: '2026-08-28T00:00:00Z', updatedAt: '2026-08-28T00:00:00Z',
  }
}

export function makeValidation(overrides: Partial<CareerAbilityValidation> = {}): CareerAbilityValidation {
  return {
    id: 'validation-1', sessionId: 'session-1', goalId: 'goal-1', planId: 'plan-1', nodeId: 'skill-oop',
    canvasVersionId: 'canvas-v3', method: 'SCENARIO', status: 'EVALUATED', submission: { summary: '场景说明' },
    score: { overall: 82 }, result: 'PASSED', feedback: { summary: '事实充分', strengths: ['结构清晰'], gaps: [] },
    userConfirmed: false, evidenceIds: [], promptVersion: 'cp-v1', schemaVersion: 'validation-v1', model: 'mock-model',
    createdAt: '2026-08-28T00:00:00Z', evaluatedAt: '2026-08-28T00:00:01Z', ...overrides,
  }
}

export function makeVersions(): CanvasVersion[] {
  return [3, 2, 1].map(version => ({
    versionId: `canvas-v${version}`, version, parentVersionId: version > 1 ? `canvas-v${version - 1}` : null,
    reason: version === 1 ? 'GOAL_CONFIRMED' : version === 2 ? 'AI_GENERATED' : 'USER_EDIT',
    changeSummary: version === 3 ? '更新能力节点' : version === 2 ? '生成完整能力树' : '确认目标职业',
    graphHash: `hash-${version}`, createdBy: version === 2 ? 'AI' : 'USER', promptVersion: 'cp-v1',
    schemaVersion: 'career-canvas-v1', model: version === 2 ? 'mock-model' : null,
    createdAt: `2026-08-2${version}T00:00:00Z`,
  }))
}

export function makeProposal(canvas = makeCanvas()): CareerCanvasProposal {
  return {
    id: 'proposal-1', sessionId: 'session-1', goalId: 'goal-1', baseVersionId: canvas.versionId,
    baseVersion: canvas.version, status: 'DRAFT', instruction: '补强云原生能力', promptVersion: 'cp-v1',
    schemaVersion: 'proposal-v1', model: 'mock-model', createdAt: '2026-08-28T00:00:00Z',
    items: [
      { id: 'proposal-item-1', sequence: 1, proposalKey: 'add-cloud', operation: 'ADD', parentNodeId: 'domain-java', before: {}, after: { title: '云原生部署' }, reason: '目标岗位需要', sourceRefs: ['jd:1'], impactNodeIds: [], decision: 'PENDING' },
      { id: 'proposal-item-2', sequence: 2, proposalKey: 'update-api', operation: 'UPDATE', targetNodeId: 'task-api', before: { title: '实现状态流转 API' }, after: { title: '实现可观测 API' }, reason: '增加验证结果', sourceRefs: ['profile:1'], impactNodeIds: ['task-api'], decision: 'PENDING' },
    ],
  }
}

export function makeSession(canvas = makeCanvas()): CareerPlanningSession {
  return {
    id: 'session-1', status: 'ACTIVE', phase: 'CANVAS', entryMode: 'KNOWN_TARGET', aiConsent: true, version: 5,
    profile: { id: 'profile-1', status: 'CONFIRMED', entryMode: 'KNOWN_TARGET', basics: {}, preferences: {}, constraints: {}, snapshotHash: 'profile-hash', snapshotVersion: 2, version: 2, items: [], updatedAt: '2026-08-28T00:00:00Z', confirmedAt: '2026-08-28T00:00:00Z' },
    permissions: [], interviewRounds: [], messages: [], recommendationSet: {
      id: 'set-1', status: 'READY', profileSnapshotHash: 'profile-hash', insufficientReasons: [], promptVersion: 'cp-v1', createdAt: '2026-08-28T00:00:00Z',
      recommendations: [
        { id: 'recommendation-1', taxonomyNodeId: 'backend', title: '后端开发', tier: 'READY_NOW', fitSummary: '当前目标', rationale: [], gaps: [], sourceRefs: [], favorite: false, sortOrder: 0 },
        { id: 'recommendation-2', taxonomyNodeId: 'platform', title: '平台工程师', tier: 'AFTER_SMALL_GAP', fitSummary: '工程能力接近', rationale: ['工程化基础'], gaps: ['云平台'], sourceRefs: ['profile:1'], favorite: false, sortOrder: 1 },
      ],
    },
    activeGoal: { id: 'goal-1', recommendationId: 'recommendation-1', taxonomyNodeId: 'backend', title: '后端开发', status: 'ACTIVE', version: 1, confirmedAt: '2026-08-28T00:00:00Z' },
    canvas, createdAt: '2026-08-28T00:00:00Z', updatedAt: '2026-08-28T00:00:00Z',
  }
}

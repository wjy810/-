import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { makeCanvas, makePlan, makeValidation } from '../../../../tests/careerPlanningFixtures'
import { SelectStub } from '../../../../tests/vueStubs'

const api = vi.hoisted(() => ({
  confirmCareerAbilityValidation: vi.fn(),
  fetchCareerAbilityValidations: vi.fn(),
  generateCareerValidationQuiz: vi.fn(),
  startCareerAbilityValidation: vi.fn(),
  startCareerValidationBatch: vi.fn(),
}))
const tasks = vi.hoisted(() => ({ fetchTask: vi.fn(), retryTask: vi.fn() }))
const polling = vi.hoisted(() => ({ pollTask: vi.fn(), isAbortError: vi.fn(() => false) }))
vi.mock('../services/careerPlanningApi', () => api)
vi.mock('@/shared/api/task', () => tasks)
vi.mock('@/shared/lib/pollTask', () => polling)
const library = vi.hoisted(() => ({ createCareerRecord: vi.fn() }))
vi.mock('@/features/career-library/services/careerLibraryApi', () => library)

import CareerValidationWorkspace from './CareerValidationWorkspace.vue'

describe('CareerValidationWorkspace', () => {
  beforeEach(() => {
    Object.values(api).forEach(mock => mock.mockReset())
    Object.values(tasks).forEach(mock => mock.mockReset())
    Object.values(polling).forEach(mock => mock.mockReset())
    library.createCareerRecord.mockReset()
    polling.isAbortError.mockReturnValue(false)
    window.sessionStorage.clear()
  })

  it('runs a quiz the AI sets, sends the answers, and offers to save a confirmed skill to the library', async () => {
    const canvas = makeCanvas()
    api.generateCareerValidationQuiz.mockResolvedValue({ nodeId: 'skill-oop', questions: [
      { id: 'q1', question: '什么是单一职责？', focus: '概念' },
      { id: 'q2', question: '举例说明接口隔离。', focus: '应用' },
      { id: 'q3', question: '过度拆分有什么代价？', focus: '边界' },
    ] })
    const evaluated = { ...makeValidation(), method: 'QUIZ' }
    api.startCareerAbilityValidation.mockResolvedValue(evaluated)
    const wrapper = mount(CareerValidationWorkspace, {
      props: { sessionId: 'session-1', canvas, plan: makePlan(canvas), validations: [], initialNodeId: 'skill-oop' },
      global: { stubs: { UiSelect: SelectStub } },
    })

    await wrapper.get('.method-grid button:nth-child(1)').trigger('click')
    await wrapper.get('.quiz-generate').trigger('click')
    await flushPromises()
    expect(api.generateCareerValidationQuiz).toHaveBeenCalledWith('session-1', { nodeId: 'skill-oop', requestId: expect.any(String) })
    const answers = wrapper.findAll('.quiz-block textarea')
    expect(answers).toHaveLength(3)

    await answers[0].setValue('一个类只因为一种原因而变化')
    await wrapper.get('.evaluate-button').trigger('click')
    await flushPromises()
    expect(api.startCareerAbilityValidation).not.toHaveBeenCalled()
    expect(wrapper.emitted('error')?.at(-1)?.[0]).toContain('回答全部题目')

    await answers[1].setValue('把支付和退款接口拆成两个调用方各自依赖的接口')
    await answers[2].setValue('类和调用链变多，理解与追踪的成本上升')
    await wrapper.get('.evaluate-button').trigger('click')
    await flushPromises()
    expect(api.startCareerAbilityValidation).toHaveBeenCalledWith('session-1', expect.objectContaining({
      method: 'QUIZ',
      submission: { summary: '专项测试作答', quiz: [
        { question: '什么是单一职责？', answer: '一个类只因为一种原因而变化' },
        { question: '举例说明接口隔离。', answer: '把支付和退款接口拆成两个调用方各自依赖的接口' },
        { question: '过度拆分有什么代价？', answer: '类和调用链变多，理解与追踪的成本上升' },
      ] },
    }))

    const confirmed = { ...evaluated, status: 'CONFIRMED', result: 'PASSED', userConfirmed: true }
    library.createCareerRecord.mockResolvedValue({ id: 'record-1' })
    await wrapper.setProps({ validations: [confirmed] })
    await wrapper.get('.save-library').trigger('click')
    await flushPromises()
    expect(library.createCareerRecord).toHaveBeenCalledWith(expect.objectContaining({ type: 'SKILL', title: '面向对象建模' }))
    expect(wrapper.get('.save-library').text()).toContain('已写进资料库')
    expect(wrapper.get('.save-library').attributes('disabled')).toBeDefined()
  })

  it('submits a scenario for AI evaluation but requires a separate user confirmation', async () => {
    const canvas = makeCanvas()
    const evaluated = makeValidation()
    api.startCareerAbilityValidation.mockResolvedValue(evaluated)
    api.confirmCareerAbilityValidation.mockResolvedValue({ ...evaluated, status: 'CONFIRMED', userConfirmed: true })
    const wrapper = mount(CareerValidationWorkspace, {
      props: { sessionId: 'session-1', canvas, plan: makePlan(canvas), validations: [], initialNodeId: 'skill-oop' },
      global: { stubs: { UiSelect: SelectStub } },
    })

    await wrapper.get('.method-grid button:nth-child(3)').trigger('click')
    await wrapper.get('.submission-field textarea').setValue('我在订单系统中划分了聚合职责，并用测试验证边界。')
    await wrapper.get('.evaluate-button').trigger('click')
    await flushPromises()

    expect(api.startCareerAbilityValidation).toHaveBeenCalledWith('session-1', {
      requestId: expect.any(String),
      nodeId: 'skill-oop',
      method: 'SCENARIO',
      evidenceIds: [],
      submission: { summary: '我在订单系统中划分了聚合职责，并用测试验证边界。' },
      expectedCanvasVersion: 3,
    })
    expect(api.confirmCareerAbilityValidation).not.toHaveBeenCalled()
    expect(wrapper.emitted('updated')?.[0]).toEqual([evaluated, true])

    await wrapper.setProps({ validations: [evaluated] })
    await wrapper.get('.evaluation-result footer button:last-child').trigger('click')
    await flushPromises()
    expect(api.confirmCareerAbilityValidation).toHaveBeenCalledWith('session-1', 'validation-1', true, 3)
  })

  it('submits 2–8 node materials in one batch and restores results by the returned batch id', async () => {
    const canvas = makeCanvas()
    const values = [
      makeValidation({ id: 'validation-oop', nodeId: 'skill-oop', batchId: 'batch-1', result: 'NEEDS_WORK' }),
      makeValidation({ id: 'validation-api', nodeId: 'task-api', batchId: 'batch-1', result: 'INSUFFICIENT' }),
    ]
    api.startCareerValidationBatch.mockResolvedValue({ id: 'task-batch', status: 'PENDING', progressPercent: 0 })
    polling.pollTask.mockImplementation(async (_id, onTick) => {
      const task = { id: 'task-batch', status: 'SUCCEEDED', progressPercent: 100, resultVersion: 'batch-1' }
      onTick(task)
      return task
    })
    api.fetchCareerAbilityValidations.mockResolvedValue(values)
    const wrapper = mount(CareerValidationWorkspace, {
      props: {
        sessionId: 'session-1', canvas, plan: makePlan(canvas), validations: [],
        initialNodeIds: ['skill-oop', 'task-api'],
      },
      global: { stubs: { UiSelect: SelectStub } },
    })

    await wrapper.get('.submission-field textarea').setValue('说明面向对象建模过程和测试结果。')
    await wrapper.findAll('.batch-queue button')[1].trigger('click')
    await wrapper.get('.submission-field textarea').setValue('说明接口状态流转实现及可复核结果。')
    await wrapper.get('.evaluate-button').trigger('click')
    await flushPromises()

    expect(api.startCareerValidationBatch).toHaveBeenCalledWith('session-1', expect.objectContaining({
      requestId: expect.any(String), expectedCanvasVersion: 3, method: 'PROJECT_CHECK',
      items: [
        { nodeId: 'skill-oop', evidenceIds: [], submission: { summary: '说明面向对象建模过程和测试结果。' } },
        { nodeId: 'task-api', evidenceIds: [], submission: { summary: '说明接口状态流转实现及可复核结果。' } },
      ],
    }))
    expect(api.fetchCareerAbilityValidations).toHaveBeenCalledWith('session-1', 'batch-1')
    expect(wrapper.emitted('batchUpdated')?.[0]).toEqual([values, true])
    expect(wrapper.findAll('.batch-results article')).toHaveLength(2)
    expect(wrapper.text()).toContain('AI 结果不会自动修改掌握状态')
    expect(wrapper.text()).toContain('AI 辅助评估')
    expect(wrapper.text()).not.toContain('mock-model')
    expect(wrapper.text()).not.toContain('SYSTEM')
  })

  it('explains the failed stage in plain words and retries the original batch task', async () => {
    const failed = {
      id: 'task-failed', status: 'FAILED', progressPercent: 88,
      errorCode: 'CP_PROPOSAL_REFERENCE_INVALID', checkpointCode: 'VALIDATING_RESPONSE',
      failureReason: '节点引用无效，画布未修改',
    }
    api.startCareerValidationBatch.mockResolvedValue({ id: 'task-failed', status: 'PENDING', progressPercent: 0 })
    polling.pollTask.mockResolvedValueOnce(failed).mockResolvedValueOnce(failed)
    tasks.retryTask.mockResolvedValue({ ...failed, status: 'PENDING', progressPercent: 0 })
    const wrapper = mount(CareerValidationWorkspace, {
      props: {
        sessionId: 'session-1', canvas: makeCanvas(), plan: makePlan(), validations: [],
        initialNodeIds: ['skill-oop', 'task-api'],
      },
      global: { stubs: { UiSelect: SelectStub } },
    })
    await wrapper.get('.submission-field textarea').setValue('建模说明')
    await wrapper.findAll('.batch-queue button')[1].trigger('click')
    await wrapper.get('.submission-field textarea').setValue('接口说明')
    await wrapper.get('.evaluate-button').trigger('click')
    await flushPromises()

    const state = wrapper.get('.batch-task-state').text()
    expect(state).toContain('节点引用无效，画布未修改')
    expect(state).toContain('正在校验评估结果')
    expect(state).not.toContain('CP_PROPOSAL_REFERENCE_INVALID')
    expect(state).not.toContain('VALIDATING_RESPONSE')
    await wrapper.get('.batch-task-state button').trigger('click')
    await flushPromises()
    expect(tasks.retryTask).toHaveBeenCalledWith('task-failed')
  })

  it('recovers a completed batch from session storage without starting a second task', async () => {
    const values = [
      makeValidation({ id: 'validation-oop', nodeId: 'skill-oop', batchId: 'batch-restored' }),
      makeValidation({ id: 'validation-api', nodeId: 'task-api', batchId: 'batch-restored' }),
    ]
    window.sessionStorage.setItem('career-planning:validation-batch:session-1', JSON.stringify({
      taskId: 'task-restored', requestId: 'request-restored', nodeIds: ['skill-oop', 'task-api'],
    }))
    tasks.fetchTask.mockResolvedValue({ id: 'task-restored', status: 'SUCCEEDED', resultVersion: 'batch-restored' })
    api.fetchCareerAbilityValidations.mockResolvedValue(values)
    const wrapper = mount(CareerValidationWorkspace, {
      props: { sessionId: 'session-1', canvas: makeCanvas(), plan: makePlan(), validations: [] },
      global: { stubs: { UiSelect: SelectStub } },
    })
    await flushPromises()

    expect(api.startCareerValidationBatch).not.toHaveBeenCalled()
    expect(api.fetchCareerAbilityValidations).toHaveBeenCalledWith('session-1', 'batch-restored')
    expect(wrapper.findAll('.batch-results article')).toHaveLength(2)
  })
})

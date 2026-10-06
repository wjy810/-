import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { makeCanvas, makeProposal } from '../../../../tests/careerPlanningFixtures'
import { ModalStub } from '../../../../tests/vueStubs'

const api = vi.hoisted(() => ({
  decideCareerCanvasProposal: vi.fn(),
  discardCareerCanvasProposal: vi.fn(),
  generateCareerCanvasProposal: vi.fn(),
}))
vi.mock('../services/careerPlanningApi', () => api)

import CareerProposalReview from './CareerProposalReview.vue'

describe('CareerProposalReview', () => {
  beforeEach(() => {
    api.decideCareerCanvasProposal.mockReset()
    api.discardCareerCanvasProposal.mockReset()
    api.generateCareerCanvasProposal.mockReset()
  })

  it('does not apply AI changes until every proposal item is explicitly decided', async () => {
    const canvas = makeCanvas()
    const proposal = makeProposal(canvas)
    const appliedCanvas = makeCanvas(4)
    api.decideCareerCanvasProposal.mockResolvedValue({ proposal: { ...proposal, status: 'ACCEPTED' }, canvas: appliedCanvas })

    const wrapper = mount(CareerProposalReview, {
      props: { open: true, sessionId: 'session-1', canvas, initialProposal: proposal },
      global: { stubs: { AppModal: ModalStub } },
    })

    expect(api.decideCareerCanvasProposal).not.toHaveBeenCalled()
    expect(wrapper.get('.proposal-apply').attributes('disabled')).toBeDefined()
    await wrapper.get('.proposal-secondary').trigger('click')
    expect(wrapper.get('.proposal-apply').attributes('disabled')).toBeUndefined()
    await wrapper.get('.proposal-apply').trigger('click')
    await flushPromises()

    expect(api.decideCareerCanvasProposal).toHaveBeenCalledWith('session-1', 'proposal-1', [
      { itemId: 'proposal-item-1', decision: 'ACCEPTED', rejectionReason: undefined },
      { itemId: 'proposal-item-2', decision: 'ACCEPTED', rejectionReason: undefined },
    ], 3)
    expect(wrapper.emitted('applied')?.[0]?.[0].canvas.version).toBe(4)
  })

  it('renders prerequisite relations and lets the user discard a stale inference proposal', async () => {
    const canvas = makeCanvas()
    const proposal = {
      ...makeProposal(canvas), status: 'STALE' as const, proposalType: 'NODE_INFERENCE' as const,
      targetNodeId: 'skill-oop', direction: 'PREREQUISITES' as const, depth: 'ONE_LEVEL' as const,
      items: [
        { ...makeProposal(canvas).items[0], proposalKey: 'add-cloud', after: { title: '云原生基础' } },
        {
          id: 'relation-item', sequence: 2, proposalKey: 'link-cloud', operation: 'ADD_RELATION' as const,
          before: {}, after: { relationType: 'PREREQUISITE', fromNodeId: 'PROPOSAL:add-cloud', toNodeId: 'skill-oop' },
          reason: '作为当前技能的前置能力', sourceRefs: ['goal:1'], impactNodeIds: ['skill-oop'], decision: 'PENDING' as const,
        },
      ],
    }
    api.discardCareerCanvasProposal.mockResolvedValue({ ...proposal, status: 'DISCARDED' })
    const wrapper = mount(CareerProposalReview, {
      props: { open: true, sessionId: 'session-1', canvas, initialProposal: proposal },
      global: { stubs: { AppModal: ModalStub } },
    })

    expect(wrapper.text()).toContain('节点推演提案')
    expect(wrapper.text()).toContain('云原生基础 → 面向对象建模')
    expect(wrapper.get('[role="alert"]').text()).toContain('提案已过期')
    await wrapper.get('.proposal-discard').trigger('click')
    await flushPromises()

    expect(api.discardCareerCanvasProposal).toHaveBeenCalledWith('session-1', 'proposal-1')
    expect(wrapper.emitted('discarded')).toHaveLength(1)
    expect(wrapper.emitted('close')).toHaveLength(1)
  })
})

import { defineComponent } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { makeCanvas } from '../../../../tests/careerPlanningFixtures'
import { SelectStub } from '../../../../tests/vueStubs'

const api = vi.hoisted(() => ({
  addCareerCanvasRelation: vi.fn(),
  createCareerCanvasNode: vi.fn(),
  deleteCareerCanvasNode: vi.fn(),
  fetchCareerCanvasProposal: vi.fn(),
  fetchCareerPlanningSession: vi.fn(),
  mergeCareerCanvasNodes: vi.fn(),
  splitCareerCanvasNode: vi.fn(),
  restoreCareerCanvasVersion: vi.fn(),
  startCareerCanvasGeneration: vi.fn(),
  startCareerNodeInference: vi.fn(),
  updateCareerCanvasNode: vi.fn(),
  updateCareerCanvasNodes: vi.fn(),
}))
vi.mock('../services/careerPlanningApi', () => api)

vi.mock('@vue-flow/core', () => ({
  Handle: defineComponent({ name: 'Handle', template: '<span />' }),
  VueFlow: defineComponent({
    name: 'VueFlow',
    props: ['panOnDrag', 'selectionMode', 'selectionKeyCode', 'multiSelectionKeyCode'],
    template: '<div class="vue-flow-stub"><slot /></div>',
  }),
  MarkerType: { ArrowClosed: 'arrowclosed' },
  Position: { Left: 'left', Right: 'right' },
  SelectionMode: { Full: 'full', Partial: 'partial' },
  useVueFlow: () => ({ fitView: vi.fn() }),
}))

import CareerAbilityCanvas from './CareerAbilityCanvas.vue'

describe('CareerAbilityCanvas', () => {
  beforeEach(() => {
    Object.values(api).forEach(mock => mock.mockReset())
    window.localStorage.clear()
    window.sessionStorage.clear()
  })

  it('animates only a real expansion, cancels rapid changes, and respects reduced motion', async () => {
    const wrapper = mount(CareerAbilityCanvas, {
      props: { sessionId: 'motion-session', canvas: makeCanvas(), goalTitle: '后端开发', aiConsent: true },
      global: { stubs: { AppSelect: SelectStub, Background: true, MiniMap: true, Controls: true } },
    })
    const stage = wrapper.get('.canvas-desktop-stage').element
    const element = document.createElement('div')
    element.className = 'vue-flow__node'
    element.dataset.id = 'skill-oop'
    const card = document.createElement('button')
    card.className = 'flow-card'
    const cancel = vi.fn()
    const animate = vi.fn(() => ({ cancel }))
    Object.defineProperty(card, 'animate', { value: animate })
    element.append(card)
    stage.append(element)
    const component = wrapper.vm as unknown as {
      toggleDesktopCollapsed: (id: string) => void
      collapseToDomains: () => void
    }
    expect(animate).not.toHaveBeenCalled()
    component.toggleDesktopCollapsed('domain-java')
    await flushPromises()
    expect(animate).not.toHaveBeenCalled()
    component.toggleDesktopCollapsed('domain-java')
    await flushPromises()
    expect(animate).toHaveBeenCalledOnce()
    expect(animate.mock.calls[0]?.[1]).toMatchObject({ duration: 240, delay: 40, fill: 'backwards' })
    component.collapseToDomains()
    expect(cancel).toHaveBeenCalledOnce()
    const media = vi.mocked(window.matchMedia).mock.results.findLast(result => result.value.media === '(prefers-reduced-motion: reduce)')!.value
    media.matches = true
    media.addEventListener.mock.calls[0][1]()
    component.toggleDesktopCollapsed('domain-java')
    await flushPromises()
    expect(animate).toHaveBeenCalledOnce()
  })

  it('keeps prerequisite edges static and highlights the selected ancestor path without changing statuses', async () => {
    const canvas = makeCanvas()
    canvas.relations.push({ id: 'dependency', type: 'PREREQUISITE', fromNodeId: 'skill-oop', toNodeId: 'task-api' })
    const wrapper = mount(CareerAbilityCanvas, {
      props: { sessionId: 'path-session', canvas, goalTitle: '后端开发', aiConsent: true },
      global: { stubs: { AppSelect: SelectStub, Background: true, MiniMap: true, Controls: true } },
    })
    const component = wrapper.vm as unknown as {
      selectedNodeIds: Set<string>
      flowEdges: Array<{ id: string; animated: boolean; style: { stroke: string; opacity?: number } }>
    }
    component.selectedNodeIds = new Set(['skill-oop'])
    await wrapper.vm.$nextTick()
    expect(component.flowEdges.every(edge => !edge.animated)).toBe(true)
    expect(component.flowEdges.find(edge => edge.id === 'r2')?.style.stroke).toBe('var(--color-primary)')
    expect(component.flowEdges.find(edge => edge.id === 'r3')?.style.opacity).toBe(.46)
    expect(canvas.nodes.find(node => node.logicalNodeId === 'skill-oop')?.status).toBe('PENDING_VALIDATION')
  })

  it('defaults to the standard scale and sends the selected deep scale to generation', async () => {
    const canvas = makeCanvas(1)
    canvas.nodes = canvas.nodes.filter(node => node.type === 'CAREER')
    canvas.relations = []
    api.startCareerCanvasGeneration.mockResolvedValue({
      id: 'generation-task', status: 'FAILED', progressPercent: 100,
      failureReason: 'synthetic failure', checkpointCode: 'FAILED',
    })

    const wrapper = mount(CareerAbilityCanvas, {
      props: { sessionId: 'session-1', canvas, goalTitle: '后端开发', aiConsent: true },
      global: { stubs: { AppSelect: SelectStub, Background: true, MiniMap: true, Controls: true } },
    })

    expect(wrapper.get('.generation-scale button[aria-checked="true"]').text()).toContain('标准')
    await wrapper.findAll('.generation-scale button')[2].trigger('click')
    expect(wrapper.get('.scale-summary').text()).toContain('8–10 个能力域')
    await wrapper.get('.canvas-primary').trigger('click')
    await flushPromises()

    expect(api.startCareerCanvasGeneration).toHaveBeenCalledWith('session-1', 1, 'DEEP')
  })

  it('names the mobile generation icon button for keyboard and assistive technology users', () => {
    const canvas = makeCanvas(1)
    canvas.nodes = canvas.nodes.filter(node => node.type === 'CAREER')
    canvas.relations = []

    const wrapper = mount(CareerAbilityCanvas, {
      props: { sessionId: 'session-1', canvas, goalTitle: '后端开发', aiConsent: true },
      global: {
        stubs: {
          AppSelect: SelectStub,
          Background: true,
          MiniMap: true,
          Controls: true,
        },
      },
    })

    expect(wrapper.get('.mobile-generation-card button').attributes('aria-label')).toBe('生成完整能力树')
  })

  it('virtualizes a 100-node mobile tree and supports multi-selection without mounting every row', async () => {
    const canvas = makeCanvas()
    const generated = Array.from({ length: 100 }, (_, index) => ({
      logicalNodeId: `task-${index}`,
      type: 'TASK' as const,
      status: 'NOT_STARTED' as const,
      title: `验收任务 ${index + 1}`,
      detail: { summary: `第 ${index + 1} 项验证任务` },
      sourceRefs: [], x: 0, y: index * 60, locked: false, sortOrder: index + 10,
    }))
    canvas.nodes.push(...generated)
    canvas.relations.push(...generated.map((node, index) => ({
      id: `generated-relation-${index}`,
      fromNodeId: node.logicalNodeId,
      toNodeId: 'domain-java',
      type: 'TREE_PARENT' as const,
    })))

    const wrapper = mount(CareerAbilityCanvas, {
      props: { sessionId: 'session-1', canvas, goalTitle: '后端开发', aiConsent: true },
      global: {
        stubs: {
          AppSelect: SelectStub,
          Background: true,
          MiniMap: true,
          Controls: true,
        },
      },
    })

    const initialRows = wrapper.findAll('.mobile-tree-row')
    expect(canvas.nodes.length).toBeGreaterThan(100)
    expect(initialRows.length).toBeGreaterThan(5)
    expect(initialRows.length).toBeLessThan(30)

    await wrapper.get('.mobile-canvas-filters button:nth-child(4)').trigger('click')
    const selectableRows = wrapper.findAll('.mobile-tree-row').filter(row => !row.text().includes('目标职业'))
    await selectableRows[0].get('.mobile-tree-row__main').trigger('click')
    expect((wrapper.vm as unknown as { selectedNodeId: string }).selectedNodeId).toBe('')
    await selectableRows[1].get('.mobile-tree-row__main').trigger('click')
    expect(wrapper.get('.canvas-batch-bar').text()).toContain('可验证 1 个 · 不支持 1 个')
  })

  it('shows a saved user note when the node returns to read-only mode', async () => {
    const canvas = makeCanvas()
    const domain = canvas.nodes.find(node => node.logicalNodeId === 'domain-java')!
    domain.detail = { ...domain.detail, notes: '需要通过代码、测试和复盘记录共同验证。' }

    const wrapper = mount(CareerAbilityCanvas, {
      props: { sessionId: 'session-1', canvas, goalTitle: '后端开发', aiConsent: true },
      global: {
        stubs: {
          AppSelect: SelectStub,
          Background: true,
          MiniMap: true,
          Controls: true,
        },
      },
    })

    const domainRow = wrapper.findAll('.mobile-tree-row').find(row => row.text().includes('Java 核心'))
    expect(domainRow).toBeTruthy()
    await domainRow!.get('.mobile-tree-row__main').trigger('click')
    expect((wrapper.vm as unknown as { selectedNodeId: string }).selectedNodeId).toBe('domain-java')

    const drawer = wrapper.get('[aria-label="能力节点详情"]')
    expect(drawer.text()).toContain('用户备注')
    expect(drawer.text()).toContain('需要通过代码、测试和复盘记录共同验证。')
  })

  it('opens the inference configuration for a selected node and submits the chosen scope', async () => {
    const canvas = makeCanvas()
    api.startCareerNodeInference.mockResolvedValue({
      id: 'inference-task', status: 'FAILED', progressPercent: 100,
      failureReason: 'synthetic failure', checkpointCode: 'FAILED',
    })
    const wrapper = mount(CareerAbilityCanvas, {
      props: { sessionId: 'session-1', canvas, goalTitle: '后端开发', aiConsent: true },
      global: { stubs: { AppSelect: SelectStub, Background: true, MiniMap: true, Controls: true } },
    })

    const skillRow = wrapper.findAll('.mobile-tree-row').find(row => row.text().includes('面向对象建模'))!
    await skillRow.get('.mobile-tree-row__main').trigger('click')
    const inferenceAction = wrapper.findAll('button').find(button => button.text().includes('AI 推演此节点'))!
    await inferenceAction.trigger('click')
    expect(wrapper.get('.inference-editor').text()).toContain('只形成可审阅候选')
    await wrapper.findAll('.inference-options button')[1].trigger('click')
    await wrapper.findAll('.inference-depth button')[1].trigger('click')
    await wrapper.get('.inference-editor textarea').setValue('优先补齐可验证的基础能力')
    await wrapper.get('.inference-editor').trigger('submit')
    await flushPromises()

    expect(api.startCareerNodeInference).toHaveBeenCalledWith('session-1', {
      targetNodeId: 'skill-oop', direction: 'PREREQUISITES', depth: 'FULL_BRANCH',
      instruction: '优先补齐可验证的基础能力', expectedVersion: 3,
    })
  })

  it('emits the selected skill when entering the existing validation workspace', async () => {
    const wrapper = mount(CareerAbilityCanvas, {
      props: { sessionId: 'session-1', canvas: makeCanvas(), goalTitle: '后端开发', aiConsent: true },
      global: { stubs: { AppSelect: SelectStub, Background: true, MiniMap: true, Controls: true } },
    })

    const skillRow = wrapper.findAll('.mobile-tree-row').find(row => row.text().includes('面向对象建模'))!
    await skillRow.get('.mobile-tree-row__main').trigger('click')
    const validationAction = wrapper.findAll('button').find(button => button.text().includes('AI 验证此节点'))!
    await validationAction.trigger('click')

    expect(wrapper.emitted('validate')?.[0]).toEqual([['skill-oop']])
  })

  it('switches hand and full-containment marquee tools without changing the canvas layout', async () => {
    const wrapper = mount(CareerAbilityCanvas, {
      props: { sessionId: 'session-1', canvas: makeCanvas(), goalTitle: '后端开发', aiConsent: true },
      global: { stubs: { AppSelect: SelectStub, Background: true, MiniMap: true, Controls: true } },
    })

    const flow = wrapper.getComponent({ name: 'VueFlow' })
    expect(flow.props('panOnDrag')).toBe(true)
    expect(flow.props('selectionMode')).toBe('full')
    await wrapper.findAll('.canvas-tool-toggle button')[1].trigger('click')
    expect(flow.props('panOnDrag')).toEqual([1, 2])
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'h' }))
    await wrapper.vm.$nextTick()
    expect(flow.props('panOnDrag')).toBe(true)
  })

  it('keeps modified card clicks out of Vue Flow so multi-selection is handled once', () => {
    const wrapper = mount(CareerAbilityCanvas, {
      props: { sessionId: 'session-1', canvas: makeCanvas(), goalTitle: '后端开发', aiConsent: true },
      global: { stubs: { AppSelect: SelectStub, Background: true, MiniMap: true, Controls: true } },
    })
    const stopPropagation = vi.fn()

    ;(wrapper.vm as unknown as { onFlowCardPointerDown: (event: Partial<PointerEvent>) => void })
      .onFlowCardPointerDown({ ctrlKey: true, metaKey: false, shiftKey: false, stopPropagation })

    expect(stopPropagation).toHaveBeenCalledOnce()
  })

  it('accepts Vue Flow selection changes only while the marquee is active', async () => {
    const wrapper = mount(CareerAbilityCanvas, {
      props: { sessionId: 'session-1', canvas: makeCanvas(), goalTitle: '后端开发', aiConsent: true },
      global: { stubs: { AppSelect: SelectStub, Background: true, MiniMap: true, Controls: true } },
    })
    const component = wrapper.vm as unknown as {
      onNodesChange: (changes: Array<{ type: 'select'; id: string; selected: boolean }>) => void
      onSelectionStart: (event: Partial<MouseEvent>) => void
      onSelectionEnd: () => void
      selectedNodeIds: Set<string>
    }

    component.onNodesChange([{ type: 'select', id: 'skill-oop', selected: true }])
    expect(component.selectedNodeIds.size).toBe(0)
    component.onSelectionStart({ target: document.createElement('div') })
    component.onNodesChange([{ type: 'select', id: 'skill-oop', selected: true }])
    await wrapper.vm.$nextTick()
    expect(component.selectedNodeIds.has('skill-oop')).toBe(true)
    component.onSelectionEnd()

    const nodeTarget = document.createElement('button')
    nodeTarget.className = 'vue-flow__node'
    component.onSelectionStart({ target: nodeTarget })
    component.onNodesChange([{ type: 'select', id: 'skill-oop', selected: false }])
    expect(component.selectedNodeIds.has('skill-oop')).toBe(true)
  })

  it('emits 2–8 eligible nodes as one validation selection and reports an explicit over-limit error', async () => {
    const canvas = makeCanvas()
    canvas.nodes.push(...Array.from({ length: 8 }, (_, index) => ({
      logicalNodeId: `extra-task-${index}`, type: 'TASK' as const, status: 'PLANNED' as const,
      title: `批量任务 ${index + 1}`, detail: {}, sourceRefs: [], x: 0, y: 0, locked: false, sortOrder: 10 + index,
    })))
    const wrapper = mount(CareerAbilityCanvas, {
      props: { sessionId: 'session-1', canvas, goalTitle: '后端开发', aiConsent: true },
      global: { stubs: { AppSelect: SelectStub, Background: true, MiniMap: true, Controls: true } },
    })

    await wrapper.get('.mobile-canvas-filters button:nth-child(4)').trigger('click')
    const rows = wrapper.findAll('.mobile-tree-row').filter(row => row.text().includes('批量任务'))
    await rows[0].get('.mobile-tree-row__main').trigger('click')
    await rows[1].get('.mobile-tree-row__main').trigger('click')
    await wrapper.get('.batch-validate').trigger('click')
    expect(wrapper.emitted('validate')?.at(-1)?.[0]).toEqual(['extra-task-0', 'extra-task-1'])

    for (const row of rows.slice(2, 8)) await row.get('.mobile-tree-row__main').trigger('click')
    const skill = wrapper.findAll('.mobile-tree-row').find(row => row.text().includes('面向对象建模'))!
    await skill.get('.mobile-tree-row__main').trigger('click')
    await wrapper.get('.batch-validate').trigger('click')
    expect(wrapper.emitted('error')?.at(-1)?.[0]).toContain('减少到 8 个以内')
  })
})

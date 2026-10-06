import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { makeCanvas, makeVersions } from '../../../../tests/careerPlanningFixtures'

const api = vi.hoisted(() => ({
  compareCareerCanvasVersions: vi.fn(),
  fetchCareerCanvasVersion: vi.fn(),
  fetchCareerCanvasVersions: vi.fn(),
  restoreCareerCanvasVersion: vi.fn(),
}))
vi.mock('../services/careerPlanningApi', () => api)

import CareerVersionHistory from './CareerVersionHistory.vue'

describe('CareerVersionHistory', () => {
  beforeEach(() => Object.values(api).forEach(mock => mock.mockReset()))

  it('compares an immutable historical version and restores it by creating a new version', async () => {
    const current = makeCanvas(3)
    const old = makeCanvas(2)
    const restored = makeCanvas(4)
    api.fetchCareerCanvasVersions.mockResolvedValue({ items: makeVersions(), page: 0, size: 20, totalElements: 3, totalPages: 1, hasNext: false })
    api.fetchCareerCanvasVersion.mockResolvedValue(old)
    api.compareCareerCanvasVersions.mockResolvedValue({
      fromVersion: 2, toVersion: 3, addedNodes: 1, removedNodes: 0, updatedNodes: 1, movedNodes: 0,
      addedRelations: 1, removedRelations: 0, nodes: [], relations: [],
    })
    api.restoreCareerCanvasVersion.mockResolvedValue(restored)

    const wrapper = mount(CareerVersionHistory, { props: { sessionId: 'session-1', canvas: current } })
    await flushPromises()
    const versionTwo = wrapper.findAll('.version-timeline li button').find(button => button.text().includes('v2'))
    expect(versionTwo).toBeDefined()
    await versionTwo!.trigger('click')
    await flushPromises()
    expect(api.compareCareerCanvasVersions).toHaveBeenCalledWith('session-1', 2, 3)
    expect(wrapper.text()).toContain('相对新增')

    await wrapper.get('.version-detail>header>button').trigger('click')
    await flushPromises()
    expect(api.restoreCareerCanvasVersion).toHaveBeenCalledWith('session-1', 2, 3)
    expect(wrapper.emitted('restored')?.[0]?.[0].version).toBe(4)
    expect(wrapper.emitted('notice')?.[0]?.[0]).toContain('历史版本未被覆盖')
  })
})

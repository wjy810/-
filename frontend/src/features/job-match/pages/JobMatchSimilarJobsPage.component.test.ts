import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiClientError } from '@/shared/api/types'

const api = vi.hoisted(() => ({ fetchCareerDirections: vi.fn() }))
vi.mock('../services/jobMatchApi', () => api)
vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { id: 'match-1' }, query: {} }),
  useRouter: () => ({ push: vi.fn() }),
}))
import JobMatchSimilarJobsPage from './JobMatchSimilarJobsPage.vue'

function mountPage() {
  return mount(JobMatchSimilarJobsPage, { global: { stubs: { UiIllustration: true } } })
}

describe('similar directions', () => {
  beforeEach(() => { api.fetchCareerDirections.mockReset() })

  it('lists shared skills instead of a percentage', async () => {
    api.fetchCareerDirections.mockResolvedValue({
      resumeSkills: ['Java', 'Python', '数据挖掘与分析'],
      directions: [
        { taxonomyNodeId: 'n-1', title: '数据挖掘', category: '技术研发', sharedSkills: ['Python', '数据挖掘与分析'] },
        { taxonomyNodeId: 'n-2', title: 'Python', category: '技术研发', sharedSkills: ['Python'] },
      ],
    })
    const wrapper = mountPage()
    await flushPromises()
    const cards = wrapper.findAll('.jm-direction')
    expect(cards).toHaveLength(2)
    expect(cards[0].text()).toContain('共同技能 2 项')
    expect(cards[0].text()).toContain('数据挖掘与分析')
    expect(wrapper.text()).not.toMatch(/\d+\s*%/)
  })

  it('explains why nothing is listed', async () => {
    api.fetchCareerDirections.mockResolvedValue({ resumeSkills: [], directions: [] })
    const noSkills = mountPage()
    await flushPromises()
    expect(noSkills.text()).toContain('投递简历里还没有技能条目')

    api.fetchCareerDirections.mockResolvedValue({ resumeSkills: ['沟通'], directions: [] })
    const noOverlap = mountPage()
    await flushPromises()
    expect(noOverlap.text()).toContain('没有找到与简历技能直接对应的其他方向')
    expect(noOverlap.text()).toContain('沟通')
  })

  it('shows a failed load as an error, not as an empty result', async () => {
    api.fetchCareerDirections.mockRejectedValue(new ApiClientError({ category: 'SYSTEM_FAILURE', reason: 'X', message: '服务暂时不可用' }, 503))
    const wrapper = mountPage()
    await flushPromises()
    expect(wrapper.text()).toContain('相似方向读取失败')
    expect(wrapper.text()).not.toContain('没有找到')
  })
})

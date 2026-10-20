import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import CareerProfileTab from './CareerProfileTab.vue'
import type { CareerOverview, CareerProfile } from '../types'

vi.mock('../services/careerLibraryApi', () => ({ commitCareerAvatar: vi.fn(), deleteCareerAvatar: vi.fn(), uploadCareerAvatar: vi.fn() }))
vi.mock('@/features/ai-resume/services/aiResumeApi', () => ({ listJobTaxonomy: vi.fn(async () => []) }))

const profile: CareerProfile = {
  accountId: 'account-1',
  basics: { name: '林同学', links: [], photoNote: '证件照' },
  intentions: { targetJob: '数据分析师', taxonomyNodeId: 'job-1', source: 'ONBOARDING' },
  preferences: { targetCity: '杭州', workMode: 'HYBRID', graduationDate: '2026-06', major: '统计学', salaryRange: '15-20K' },
  summary: '', snapshotVersion: 1, version: 2, updatedAt: '2026-10-01T00:00:00Z', completeness: 60, missingItems: [],
}
const overview = { profileCompleteness: 60, missingItems: [] } as unknown as CareerOverview

describe('CareerProfileTab', () => {
  it('shows what onboarding wrote and saves it back along with fields this form does not know', async () => {
    const wrapper = mount(CareerProfileTab, {
      props: { profile, overview },
      global: { stubs: { JobTaxonomyPicker: true, UiDialog: true, UiSelect: true } },
    })
    expect((wrapper.get('#profile-graduation').element as HTMLInputElement).value).toBe('2026-06')
    expect((wrapper.get('#profile-major').element as HTMLInputElement).value).toBe('统计学')
    await wrapper.get('#profile-major').setValue('应用统计')
    await wrapper.get('#career-profile-form').trigger('submit')
    const saved = wrapper.emitted('save')?.[0]?.[0] as Record<string, Record<string, unknown>>
    expect(saved.preferences).toMatchObject({ graduationDate: '2026-06', major: '应用统计', salaryRange: '15-20K', targetCity: '杭州' })
    expect(saved.intentions).toMatchObject({ source: 'ONBOARDING', targetJob: '数据分析师' })
    expect(saved.basics).toMatchObject({ photoNote: '证件照', name: '林同学' })
  })
})
